/*
 * Persistent privileged injector for AutoClick.
 *
 * The Android app starts this once via su and keeps the process alive.
 * Commands are line-oriented on stdin; replies are "OK" / "OK evdev" /
 * "OK uinput" or "ERR …" on stdout.
 *
 *   CONFIG nativeW nativeH [rotation]
 *   TAP x y durationMs
 *   SWIPE x1 y1 x2 y2 durationMs
 *   PING
 *   QUIT
 *
 * nativeW/H are the display's natural (ROTATION_0) pixel size — the stable
 * ABS range of the virtual device. rotation is Display.getRotation() (0-3)
 * and is accepted for protocol completeness; TAP/SWIPE coordinates are
 * already converted into that native axis space in Kotlin (same conversion
 * as InputReader's DIRECT orientation-aware mapping). A two-argument
 * CONFIG is treated as rotation 0.
 *
 * Preferred inject path: the real Type-B touchscreen evdev, using a high
 * free MT slot so the user's finger (typically slot 0+) is not evicted.
 * Fallback: a long-lived uinput INPUT_PROP_DIRECT device with the same
 * natural ABS range. The uinput device is recreated only when native size
 * changes or the fd dies — never on rotation. There is no fixed 200ms
 * sleep after UI_DEV_CREATE; the first inject is retried briefly if
 * EventHub has not yet registered the device.
 *
 * Do not use this as a general shell. It only injects pointer events.
 */
#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <linux/input.h>
#include <linux/uinput.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <sys/time.h>

#ifndef EVIOCGMTSLOTS
#define EVIOCGMTSLOTS(len) _IOC(_IOC_READ, 'E', 0x0a, len)
#endif

#ifndef INPUT_PROP_DIRECT
#define INPUT_PROP_DIRECT 0x01
#endif
#ifndef INPUT_PROP_ACCELEROMETER
#define INPUT_PROP_ACCELEROMETER 0x06
#endif
#ifndef INPUT_PROP_MAX
#define INPUT_PROP_MAX 0x1f
#endif

#define NLONGS(bits) (((bits) / (8 * sizeof(unsigned long))) + 1)
#define TEST_BIT(bit, array) \
    (((array)[(bit) / (8 * sizeof(unsigned long))] >> \
      ((bit) % (8 * sizeof(unsigned long)))) & 1UL)

#define MAX_SCAN 32
#define MAX_SLOTS 16
#define FIRST_INJECT_RETRIES 20
#define FIRST_INJECT_SLEEP_US 5000
#define VIRT_SLOT_MAX 9

enum Mode { MODE_NONE = 0, MODE_EVDEV = 1, MODE_UINPUT = 2 };

static int ufd = -1;
static int evfd = -1;
static int mode = MODE_NONE;
static int native_w = 1080;
static int native_h = 1920;
static int rotation = 0;
static int tracking_id = 1;
static int uinput_created = 0;
static int last_native_w = 0;
static int last_native_h = 0;
static int our_slot = VIRT_SLOT_MAX;
static int ev_slot_max = 0;
static int ev_x_min = 0, ev_x_max = 0;
static int ev_y_min = 0, ev_y_max = 0;
static int ev_swap_xy = 0;
static int ev_have_pressure = 0;
static int ev_p_min = 0, ev_p_max = 255;
static int ev_have_btn_touch = 0;
static int uinput_ready = 0;

static int clampi(int v, int lo, int hi) {
    if (v < lo) return lo;
    if (v > hi) return hi;
    return v;
}

static int write_ev(int fd, int type, int code, int val, int retry) {
    struct input_event ev;
    memset(&ev, 0, sizeof(ev));
    ev.type = (__u16)type;
    ev.code = (__u16)code;
    ev.value = val;
    gettimeofday(&ev.time, NULL);
    ssize_t n = write(fd, &ev, sizeof(ev));
    if (n == (ssize_t)sizeof(ev)) return 1;
    if (!retry) return 0;
    for (int i = 0; i < FIRST_INJECT_RETRIES; i++) {
        usleep(FIRST_INJECT_SLEEP_US);
        n = write(fd, &ev, sizeof(ev));
        if (n == (ssize_t)sizeof(ev)) return 1;
    }
    return 0;
}

static int emit_u(int type, int code, int val) {
    int retry = !uinput_ready;
    int ok = write_ev(ufd, type, code, val, retry);
    if (ok) uinput_ready = 1;
    return ok;
}

static int emit_e(int type, int code, int val) {
    return write_ev(evfd, type, code, val, 0);
}

#ifdef UI_ABS_SETUP
static int set_abs(int code, int maxv) {
    struct uinput_abs_setup abs;
    memset(&abs, 0, sizeof(abs));
    abs.code = (__u16)code;
    abs.absinfo.minimum = 0;
    abs.absinfo.maximum = maxv > 0 ? maxv : 1;
    return ioctl(ufd, UI_ABS_SETUP, &abs) == 0 ? 0 : -1;
}
#endif

static void close_uinput(void) {
    if (ufd >= 0) {
        if (uinput_created) ioctl(ufd, UI_DEV_DESTROY);
        close(ufd);
        ufd = -1;
        uinput_created = 0;
        uinput_ready = 0;
    }
    if (mode == MODE_UINPUT) mode = MODE_NONE;
}

static void close_evdev(void) {
    if (evfd >= 0) {
        close(evfd);
        evfd = -1;
    }
    if (mode == MODE_EVDEV) mode = MODE_NONE;
}

static void close_all(void) {
    close_uinput();
    close_evdev();
}

static int bits_uinput(void) {
    if (ioctl(ufd, UI_SET_EVBIT, EV_KEY) < 0) return -1;
    if (ioctl(ufd, UI_SET_EVBIT, EV_ABS) < 0) return -1;
    if (ioctl(ufd, UI_SET_EVBIT, EV_SYN) < 0) return -1;
    ioctl(ufd, UI_SET_KEYBIT, BTN_TOUCH);
    ioctl(ufd, UI_SET_KEYBIT, BTN_TOOL_FINGER);
#ifdef UI_SET_PROPBIT
    ioctl(ufd, UI_SET_PROPBIT, INPUT_PROP_DIRECT);
#endif
    ioctl(ufd, UI_SET_ABSBIT, ABS_X);
    ioctl(ufd, UI_SET_ABSBIT, ABS_Y);
    ioctl(ufd, UI_SET_ABSBIT, ABS_MT_SLOT);
    ioctl(ufd, UI_SET_ABSBIT, ABS_MT_TRACKING_ID);
    ioctl(ufd, UI_SET_ABSBIT, ABS_MT_POSITION_X);
    ioctl(ufd, UI_SET_ABSBIT, ABS_MT_POSITION_Y);
    ioctl(ufd, UI_SET_ABSBIT, ABS_MT_PRESSURE);
    return 0;
}

static int open_uinput(void) {
    int fd = open("/dev/uinput", O_WRONLY);
    if (fd < 0) fd = open("/dev/input/uinput", O_WRONLY);
    return fd;
}

static int setup_legacy(int ax, int ay) {
    struct uinput_user_dev uidev;
    memset(&uidev, 0, sizeof(uidev));
    snprintf(uidev.name, UINPUT_MAX_NAME_SIZE, "autoclick-uinput");
    uidev.id.bustype = BUS_VIRTUAL;
    uidev.id.vendor = 0x0001;
    uidev.id.product = 0x0001;
    uidev.id.version = 1;
    uidev.absmin[ABS_X] = 0;
    uidev.absmax[ABS_X] = ax;
    uidev.absmin[ABS_Y] = 0;
    uidev.absmax[ABS_Y] = ay;
    uidev.absmin[ABS_MT_POSITION_X] = 0;
    uidev.absmax[ABS_MT_POSITION_X] = ax;
    uidev.absmin[ABS_MT_POSITION_Y] = 0;
    uidev.absmax[ABS_MT_POSITION_Y] = ay;
    uidev.absmin[ABS_MT_PRESSURE] = 0;
    uidev.absmax[ABS_MT_PRESSURE] = 255;
    uidev.absmin[ABS_MT_SLOT] = 0;
    uidev.absmax[ABS_MT_SLOT] = VIRT_SLOT_MAX;
    uidev.absmin[ABS_MT_TRACKING_ID] = 0;
    uidev.absmax[ABS_MT_TRACKING_ID] = 65535;
    if (write(ufd, &uidev, sizeof(uidev)) != (ssize_t)sizeof(uidev)) return -1;
    if (ioctl(ufd, UI_DEV_CREATE) < 0) return -1;
    return 0;
}

static int setup_uinput(int w, int h) {
    if (w < 1) w = 1;
    if (h < 1) h = 1;
    if (uinput_created && last_native_w == w && last_native_h == h && ufd >= 0) {
        native_w = w;
        native_h = h;
        mode = MODE_UINPUT;
        return 0;
    }
    close_uinput();
    native_w = w;
    native_h = h;

    ufd = open_uinput();
    if (ufd < 0) return -1;
    if (bits_uinput() < 0) {
        close_uinput();
        return -1;
    }

    int ax = w - 1;
    int ay = h - 1;
    int modern = 0;
#ifdef UI_DEV_SETUP
#ifdef UI_ABS_SETUP
    modern = 1;
    if (set_abs(ABS_X, ax) < 0) modern = 0;
    if (modern && set_abs(ABS_Y, ay) < 0) modern = 0;
    if (modern && set_abs(ABS_MT_POSITION_X, ax) < 0) modern = 0;
    if (modern && set_abs(ABS_MT_POSITION_Y, ay) < 0) modern = 0;
    if (modern && set_abs(ABS_MT_PRESSURE, 255) < 0) modern = 0;
    if (modern && set_abs(ABS_MT_SLOT, VIRT_SLOT_MAX) < 0) modern = 0;
    if (modern && set_abs(ABS_MT_TRACKING_ID, 65535) < 0) modern = 0;
    if (modern) {
        struct uinput_setup usetup;
        memset(&usetup, 0, sizeof(usetup));
        usetup.id.bustype = BUS_VIRTUAL;
        usetup.id.vendor = 0x0001;
        usetup.id.product = 0x0001;
        usetup.id.version = 1;
        snprintf(usetup.name, UINPUT_MAX_NAME_SIZE, "autoclick-uinput");
        if (ioctl(ufd, UI_DEV_SETUP, &usetup) < 0) modern = 0;
        else if (ioctl(ufd, UI_DEV_CREATE) < 0) modern = 0;
    }
#endif
#endif
    if (!modern) {
        close(ufd);
        ufd = open_uinput();
        if (ufd < 0) return -1;
        if (bits_uinput() < 0) {
            close_uinput();
            return -1;
        }
        if (setup_legacy(ax, ay) < 0) {
            close_uinput();
            return -1;
        }
    }
    uinput_created = 1;
    last_native_w = w;
    last_native_h = h;
    uinput_ready = 0;
    our_slot = VIRT_SLOT_MAX;
    mode = MODE_UINPUT;
    /* No fixed 200ms sleep. First inject retries briefly if EventHub lags. */
    return 0;
}

static int scale_axis(int v, int nmax, int amin, int amax) {
    if (nmax <= 0) return clampi(v, amin, amax);
    if (amax <= amin) return amin;
    long long r = (long long)v * (amax - amin);
    return amin + (int)(r / nmax);
}

static void map_native_to_evdev(int x, int y, int *ox, int *oy) {
    int nx = clampi(x, 0, native_w > 1 ? native_w - 1 : 0);
    int ny = clampi(y, 0, native_h > 1 ? native_h - 1 : 0);
    int xmax = native_w > 1 ? native_w - 1 : 1;
    int ymax = native_h > 1 ? native_h - 1 : 1;
    int dx, dy;
    if (ev_swap_xy) {
        dx = scale_axis(ny, ymax, ev_x_min, ev_x_max);
        dy = scale_axis(nx, xmax, ev_y_min, ev_y_max);
    } else {
        dx = scale_axis(nx, xmax, ev_x_min, ev_x_max);
        dy = scale_axis(ny, ymax, ev_y_min, ev_y_max);
    }
    *ox = dx;
    *oy = dy;
}

static int name_looks_like_touch(const char *name) {
    static const char *keys[] = {
        "touch", "fts", "goodix", "synap", "sec_touch", "himax",
        "ilitek", "nt36", "focal", "atmel", "synaptics", "gt9",
        "ts_", "touchscreen", "touch_panel", NULL
    };
    if (!name || !name[0]) return 0;
    char lower[256];
    size_t n = strlen(name);
    if (n >= sizeof(lower)) n = sizeof(lower) - 1;
    for (size_t i = 0; i < n; i++) {
        char c = name[i];
        if (c >= 'A' && c <= 'Z') c = (char)(c - 'A' + 'a');
        lower[i] = c;
    }
    lower[n] = 0;
    for (int i = 0; keys[i]; i++) {
        if (strstr(lower, keys[i])) return 1;
    }
    return 0;
}

static int evdev_other_pointers(void) {
    if (evfd < 0 || ev_slot_max < 0) return -1;
    struct {
        __u32 code;
        __s32 values[MAX_SLOTS];
    } req;
    memset(&req, 0, sizeof(req));
    req.code = ABS_MT_TRACKING_ID;
    if (ioctl(evfd, EVIOCGMTSLOTS(sizeof(req)), &req) < 0) return -1;
    int count = 0;
    int slots = ev_slot_max + 1;
    if (slots > MAX_SLOTS) slots = MAX_SLOTS;
    for (int i = 0; i < slots; i++) {
        if (i == our_slot) continue;
        if (req.values[i] >= 0) count++;
    }
    return count;
}

static int score_touchscreen(int fd, const char *name, int *out_slot_max) {
    unsigned long evbit[NLONGS(EV_MAX)];
    unsigned long absbit[NLONGS(ABS_MAX)];
    unsigned long propbit[NLONGS(INPUT_PROP_MAX)];
    unsigned long keybit[NLONGS(KEY_MAX)];
    memset(evbit, 0, sizeof(evbit));
    memset(absbit, 0, sizeof(absbit));
    memset(propbit, 0, sizeof(propbit));
    memset(keybit, 0, sizeof(keybit));
    if (ioctl(fd, EVIOCGBIT(0, sizeof(evbit)), evbit) < 0) return -1;
    if (!TEST_BIT(EV_ABS, evbit)) return -1;
    if (ioctl(fd, EVIOCGBIT(EV_ABS, sizeof(absbit)), absbit) < 0) return -1;
    if (!TEST_BIT(ABS_MT_POSITION_X, absbit) || !TEST_BIT(ABS_MT_POSITION_Y, absbit))
        return -1;
    if (!TEST_BIT(ABS_MT_SLOT, absbit) || !TEST_BIT(ABS_MT_TRACKING_ID, absbit))
        return -1;
    if (ioctl(fd, EVIOCGPROP(sizeof(propbit)), propbit) == 0) {
        if (!TEST_BIT(INPUT_PROP_DIRECT, propbit)) return -1;
        if (TEST_BIT(INPUT_PROP_ACCELEROMETER, propbit)) return -1;
    }
    if (name && strstr(name, "autoclick")) return -1;

    struct input_absinfo slotinfo, ax, ay;
    memset(&slotinfo, 0, sizeof(slotinfo));
    memset(&ax, 0, sizeof(ax));
    memset(&ay, 0, sizeof(ay));
    if (ioctl(fd, EVIOCGABS(ABS_MT_SLOT), &slotinfo) < 0) return -1;
    if (ioctl(fd, EVIOCGABS(ABS_MT_POSITION_X), &ax) < 0) return -1;
    if (ioctl(fd, EVIOCGABS(ABS_MT_POSITION_Y), &ay) < 0) return -1;
    int smax = slotinfo.maximum;
    if (smax < 0) return -1;
    if (smax >= MAX_SLOTS) smax = MAX_SLOTS - 1;
    if (out_slot_max) *out_slot_max = smax;

    int score = 10 + smax;
    if (name_looks_like_touch(name)) score += 100;
    int xspan = ax.maximum - ax.minimum;
    int yspan = ay.maximum - ay.minimum;
    if (xspan > 100 && yspan > 100) score += 20;
    (void)keybit;
    if (TEST_BIT(EV_KEY, evbit)) {
        if (ioctl(fd, EVIOCGBIT(EV_KEY, sizeof(keybit)), keybit) == 0) {
            (void)TEST_BIT(BTN_TOUCH, keybit);
        }
    }
    return score;
}

static void apply_evdev_abs(int fd, int slot_max) {
    struct input_absinfo ax, ay, ap, keyprobe;
    memset(&ax, 0, sizeof(ax));
    memset(&ay, 0, sizeof(ay));
    ioctl(fd, EVIOCGABS(ABS_MT_POSITION_X), &ax);
    ioctl(fd, EVIOCGABS(ABS_MT_POSITION_Y), &ay);
    ev_x_min = ax.minimum;
    ev_x_max = ax.maximum;
    ev_y_min = ay.minimum;
    ev_y_max = ay.maximum;
    ev_slot_max = slot_max;
    our_slot = slot_max;
    ev_swap_xy = 0;
    if (native_w > 0 && native_h > 0 && native_w != native_h) {
        int xspan = ev_x_max - ev_x_min;
        int yspan = ev_y_max - ev_y_min;
        int dxw = abs(xspan - (native_w - 1));
        int dxh = abs(xspan - (native_h - 1));
        int dyw = abs(yspan - (native_w - 1));
        int dyh = abs(yspan - (native_h - 1));
        if (dxh < dxw && dyw < dyh) ev_swap_xy = 1;
    }
    ev_have_pressure = 0;
    memset(&ap, 0, sizeof(ap));
    if (ioctl(fd, EVIOCGABS(ABS_MT_PRESSURE), &ap) == 0 && ap.maximum > ap.minimum) {
        ev_have_pressure = 1;
        ev_p_min = ap.minimum;
        ev_p_max = ap.maximum;
    }
    ev_have_btn_touch = 0;
    unsigned long evbit[NLONGS(EV_MAX)];
    unsigned long keybit[NLONGS(KEY_MAX)];
    memset(evbit, 0, sizeof(evbit));
    memset(keybit, 0, sizeof(keybit));
    if (ioctl(fd, EVIOCGBIT(0, sizeof(evbit)), evbit) == 0 && TEST_BIT(EV_KEY, evbit)) {
        if (ioctl(fd, EVIOCGBIT(EV_KEY, sizeof(keybit)), keybit) == 0 &&
            TEST_BIT(BTN_TOUCH, keybit)) {
            ev_have_btn_touch = 1;
        }
    }
    (void)keyprobe;
}

static int open_real_touchscreen(void) {
    int best_fd = -1;
    int best_score = -1;
    int best_slots = 0;
    char best_path[64] = {0};

    for (int i = 0; i < MAX_SCAN; i++) {
        char path[64];
        snprintf(path, sizeof(path), "/dev/input/event%d", i);
        int fd = open(path, O_WRONLY);
        if (fd < 0) continue;
        char name[256];
        memset(name, 0, sizeof(name));
        ioctl(fd, EVIOCGNAME(sizeof(name) - 1), name);
        int slot_max = 0;
        int score = score_touchscreen(fd, name, &slot_max);
        if (score > best_score) {
            if (best_fd >= 0) close(best_fd);
            best_fd = fd;
            best_score = score;
            best_slots = slot_max;
            snprintf(best_path, sizeof(best_path), "%s", path);
        } else {
            close(fd);
        }
    }
    if (best_fd < 0 || best_score < 0) return -1;
    evfd = best_fd;
    apply_evdev_abs(evfd, best_slots);
    mode = MODE_EVDEV;
    (void)best_path;
    return 0;
}

static int pressure_value(void) {
    if (!ev_have_pressure) return 40;
    int span = ev_p_max - ev_p_min;
    if (span <= 0) return ev_p_min + 1;
    return ev_p_min + span / 4;
}

static int evdev_down(int x, int y) {
    int dx, dy;
    map_native_to_evdev(x, y, &dx, &dy);
    int xid = tracking_id++;
    if (tracking_id > 60000) tracking_id = 1;
    int others = evdev_other_pointers();
    if (!emit_e(EV_ABS, ABS_MT_SLOT, our_slot)) return 0;
    if (!emit_e(EV_ABS, ABS_MT_TRACKING_ID, xid)) return 0;
    if (!emit_e(EV_ABS, ABS_MT_POSITION_X, dx)) return 0;
    if (!emit_e(EV_ABS, ABS_MT_POSITION_Y, dy)) return 0;
    if (ev_have_pressure) emit_e(EV_ABS, ABS_MT_PRESSURE, pressure_value());
    /* Do not emit ABS_X/Y or BTN_TOOL_FINGER — those belong to the real
     * digitizer's current (usually slot-0) pointer. BTN_TOUCH is global:
     * only raise it when no other slot is already down. */
    if (ev_have_btn_touch && others <= 0) emit_e(EV_KEY, BTN_TOUCH, 1);
    return emit_e(EV_SYN, SYN_REPORT, 0);
}

static int evdev_move(int x, int y) {
    int dx, dy;
    map_native_to_evdev(x, y, &dx, &dy);
    emit_e(EV_ABS, ABS_MT_SLOT, our_slot);
    emit_e(EV_ABS, ABS_MT_POSITION_X, dx);
    emit_e(EV_ABS, ABS_MT_POSITION_Y, dy);
    if (ev_have_pressure) emit_e(EV_ABS, ABS_MT_PRESSURE, pressure_value());
    return emit_e(EV_SYN, SYN_REPORT, 0);
}

static int evdev_up(void) {
    emit_e(EV_ABS, ABS_MT_SLOT, our_slot);
    emit_e(EV_ABS, ABS_MT_TRACKING_ID, -1);
    int others = evdev_other_pointers();
    /* others == 0: we were (or are now) the last pointer. others < 0:
     * ioctl failed — do not send BTN_TOUCH 0, which would cancel a real finger. */
    if (ev_have_btn_touch && others == 0) emit_e(EV_KEY, BTN_TOUCH, 0);
    return emit_e(EV_SYN, SYN_REPORT, 0);
}

static int uinput_down(int x, int y) {
    int xid = tracking_id++;
    if (tracking_id > 60000) tracking_id = 1;
    x = clampi(x, 0, native_w > 1 ? native_w - 1 : 0);
    y = clampi(y, 0, native_h > 1 ? native_h - 1 : 0);
    if (!emit_u(EV_ABS, ABS_MT_SLOT, our_slot)) return 0;
    if (!emit_u(EV_ABS, ABS_MT_TRACKING_ID, xid)) return 0;
    if (!emit_u(EV_ABS, ABS_MT_POSITION_X, x)) return 0;
    if (!emit_u(EV_ABS, ABS_MT_POSITION_Y, y)) return 0;
    emit_u(EV_ABS, ABS_MT_PRESSURE, 40);
    emit_u(EV_ABS, ABS_X, x);
    emit_u(EV_ABS, ABS_Y, y);
    emit_u(EV_KEY, BTN_TOUCH, 1);
    emit_u(EV_KEY, BTN_TOOL_FINGER, 1);
    return emit_u(EV_SYN, SYN_REPORT, 0);
}

static int uinput_move(int x, int y) {
    x = clampi(x, 0, native_w > 1 ? native_w - 1 : 0);
    y = clampi(y, 0, native_h > 1 ? native_h - 1 : 0);
    emit_u(EV_ABS, ABS_MT_SLOT, our_slot);
    emit_u(EV_ABS, ABS_MT_POSITION_X, x);
    emit_u(EV_ABS, ABS_MT_POSITION_Y, y);
    emit_u(EV_ABS, ABS_X, x);
    emit_u(EV_ABS, ABS_Y, y);
    return emit_u(EV_SYN, SYN_REPORT, 0);
}

static int uinput_up(void) {
    emit_u(EV_ABS, ABS_MT_SLOT, our_slot);
    emit_u(EV_ABS, ABS_MT_TRACKING_ID, -1);
    emit_u(EV_KEY, BTN_TOUCH, 0);
    emit_u(EV_KEY, BTN_TOOL_FINGER, 0);
    return emit_u(EV_SYN, SYN_REPORT, 0);
}

static int ensure_mode(void) {
    if (mode == MODE_EVDEV && evfd >= 0) return 0;
    if (mode == MODE_UINPUT && uinput_created && ufd >= 0) return 0;
    if (evfd < 0) open_real_touchscreen();
    if (evfd >= 0) {
        mode = MODE_EVDEV;
        return 0;
    }
    return setup_uinput(native_w, native_h);
}

static int down_at(int x, int y) {
    if (ensure_mode() != 0) return 0;
    if (mode == MODE_EVDEV) {
        if (evdev_down(x, y)) return 1;
        /* Real device write failed — fall back to uinput once. */
        close_evdev();
        if (setup_uinput(native_w, native_h) != 0) return 0;
        return uinput_down(x, y);
    }
    return uinput_down(x, y);
}

static int move_to(int x, int y) {
    if (mode == MODE_EVDEV && evfd >= 0) return evdev_move(x, y);
    return uinput_move(x, y);
}

static int up(void) {
    if (mode == MODE_EVDEV && evfd >= 0) return evdev_up();
    return uinput_up();
}

static void reply_ok(const char *tag) {
    if (tag && tag[0]) {
        fputs("OK ", stdout);
        fputs(tag, stdout);
        fputc('\n', stdout);
    } else {
        fputs("OK\n", stdout);
    }
    fflush(stdout);
}

static void reply_err(const char *m) {
    fputs("ERR ", stdout);
    fputs(m, stdout);
    fputc('\n', stdout);
    fflush(stdout);
}

static const char *mode_tag(void) {
    if (mode == MODE_EVDEV) return "evdev";
    if (mode == MODE_UINPUT) return "uinput";
    return NULL;
}

static int configure(int w, int h, int rot) {
    if (w < 1) w = 1;
    if (h < 1) h = 1;
    rot %= 4;
    if (rot < 0) rot += 4;
    rotation = rot;
    native_w = w;
    native_h = h;

    if (evfd < 0) open_real_touchscreen();
    if (evfd >= 0) {
        apply_evdev_abs(evfd, ev_slot_max);
        mode = MODE_EVDEV;
        return 0;
    }
    return setup_uinput(w, h);
}

static void do_tap(int x, int y, int duration_ms) {
    if (duration_ms < 1) duration_ms = 1;
    if (duration_ms > 60000) duration_ms = 60000;
    if (!down_at(x, y)) {
        reply_err("write");
        return;
    }
    usleep((useconds_t)duration_ms * 1000U);
    up();
    reply_ok(NULL);
}

static void do_swipe(int x1, int y1, int x2, int y2, int duration_ms) {
    if (duration_ms < 1) duration_ms = 1;
    if (duration_ms > 60000) duration_ms = 60000;
    if (!down_at(x1, y1)) {
        reply_err("write");
        return;
    }
    int steps = duration_ms / 8;
    if (steps < 2) steps = 2;
    if (steps > 2000) steps = 2000;
    int step_us = (int)((duration_ms * 1000.0) / steps);
    if (step_us < 1) step_us = 1;
    for (int i = 1; i <= steps; i++) {
        int x = x1 + (int)((long long)(x2 - x1) * i / steps);
        int y = y1 + (int)((long long)(y2 - y1) * i / steps);
        move_to(x, y);
        usleep((useconds_t)step_us);
    }
    up();
    reply_ok(NULL);
}

int main(void) {
    setvbuf(stdin, NULL, _IOLBF, 0);
    setvbuf(stdout, NULL, _IONBF, 0);
    signal(SIGPIPE, SIG_IGN);

    char line[256];
    while (fgets(line, sizeof(line), stdin)) {
        size_t n = strlen(line);
        while (n > 0 && (line[n - 1] == '\n' || line[n - 1] == '\r')) {
            line[--n] = 0;
        }
        if (n == 0) continue;

        int x, y, x2, y2, d, w, h, rot, ncfg;
        if (strcmp(line, "PING") == 0) {
            reply_ok(NULL);
        } else if (strcmp(line, "QUIT") == 0) {
            close_all();
            reply_ok(NULL);
            break;
        } else if ((ncfg = sscanf(line, "CONFIG %d %d %d", &w, &h, &rot)) >= 2) {
            if (ncfg != 3) rot = 0;
            if (configure(w, h, rot) == 0) reply_ok(mode_tag());
            else reply_err("uinput");
        } else if (sscanf(line, "TAP %d %d %d", &x, &y, &d) == 3) {
            if (mode == MODE_NONE && configure(native_w, native_h, rotation) != 0) {
                reply_err("uinput");
                continue;
            }
            do_tap(x, y, d);
        } else if (sscanf(line, "SWIPE %d %d %d %d %d", &x, &y, &x2, &y2, &d) == 5) {
            if (mode == MODE_NONE && configure(native_w, native_h, rotation) != 0) {
                reply_err("uinput");
                continue;
            }
            do_swipe(x, y, x2, y2, d);
        } else {
            reply_err("cmd");
        }
    }
    close_all();
    return 0;
}
