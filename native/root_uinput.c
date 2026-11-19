/*
 * Persistent privileged uinput injector for AutoClick.
 *
 * The Android app starts this once via su and keeps the process alive.
 * Commands are line-oriented on stdin; replies are "OK" or "ERR …" on stdout.
 *
 *   CONFIG w h
 *   TAP x y durationMs
 *   SWIPE x1 y1 x2 y2 durationMs
 *   PING
 *   QUIT
 *
 * Coordinates are screen pixels in the space of the last CONFIG (same space
 * as getLocationOnScreen / Accessibility dispatchGesture).
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

static int ufd = -1;
static int screen_w = 1080;
static int screen_h = 1920;
static int tracking_id = 1;
static int created = 0;
static int last_cfg_w = 0;
static int last_cfg_h = 0;

static int emit(int type, int code, int val) {
    struct input_event ev;
    memset(&ev, 0, sizeof(ev));
    ev.type = (__u16)type;
    ev.code = (__u16)code;
    ev.value = val;
    gettimeofday(&ev.time, NULL);
    ssize_t n = write(ufd, &ev, sizeof(ev));
    return n == (ssize_t)sizeof(ev);
}

static int clampi(int v, int lo, int hi) {
    if (v < lo) return lo;
    if (v > hi) return hi;
    return v;
}

static int map_x(int x) { return clampi(x, 0, screen_w > 1 ? screen_w - 1 : 0); }
static int map_y(int y) { return clampi(y, 0, screen_h > 1 ? screen_h - 1 : 0); }

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

static void close_device(void) {
    if (ufd >= 0) {
        if (created) ioctl(ufd, UI_DEV_DESTROY);
        close(ufd);
        ufd = -1;
        created = 0;
    }
}

static int bits(void) {
    if (ioctl(ufd, UI_SET_EVBIT, EV_KEY) < 0) return -1;
    if (ioctl(ufd, UI_SET_EVBIT, EV_ABS) < 0) return -1;
    if (ioctl(ufd, UI_SET_EVBIT, EV_SYN) < 0) return -1;
    ioctl(ufd, UI_SET_KEYBIT, BTN_TOUCH);
    ioctl(ufd, UI_SET_KEYBIT, BTN_TOOL_FINGER);
#ifdef UI_SET_PROPBIT
#ifdef INPUT_PROP_DIRECT
    ioctl(ufd, UI_SET_PROPBIT, INPUT_PROP_DIRECT);
#endif
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
    uidev.absmax[ABS_MT_SLOT] = 1;
    uidev.absmin[ABS_MT_TRACKING_ID] = 0;
    uidev.absmax[ABS_MT_TRACKING_ID] = 65535;
    if (write(ufd, &uidev, sizeof(uidev)) != (ssize_t)sizeof(uidev)) return -1;
    if (ioctl(ufd, UI_DEV_CREATE) < 0) return -1;
    return 0;
}

static int setup_device(int w, int h) {
    if (w < 1) w = 1;
    if (h < 1) h = 1;
    if (created && last_cfg_w == w && last_cfg_h == h) {
        screen_w = w;
        screen_h = h;
        return 0;
    }
    close_device();
    screen_w = w;
    screen_h = h;

    ufd = open_uinput();
    if (ufd < 0) return -1;
    if (bits() < 0) {
        close_device();
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
    if (modern && set_abs(ABS_MT_SLOT, 1) < 0) modern = 0;
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
        if (bits() < 0) {
            close_device();
            return -1;
        }
        if (setup_legacy(ax, ay) < 0) {
            close_device();
            return -1;
        }
    }
    created = 1;
    last_cfg_w = w;
    last_cfg_h = h;
    /* Let EventHub register the virtual device before the first inject. */
    usleep(200 * 1000);
    return 0;
}

static int down_at(int x, int y) {
    int xid = tracking_id++;
    if (tracking_id > 60000) tracking_id = 1;
    x = map_x(x);
    y = map_y(y);
    if (!emit(EV_ABS, ABS_MT_SLOT, 0)) return 0;
    if (!emit(EV_ABS, ABS_MT_TRACKING_ID, xid)) return 0;
    if (!emit(EV_ABS, ABS_MT_POSITION_X, x)) return 0;
    if (!emit(EV_ABS, ABS_MT_POSITION_Y, y)) return 0;
    emit(EV_ABS, ABS_MT_PRESSURE, 40);
    emit(EV_ABS, ABS_X, x);
    emit(EV_ABS, ABS_Y, y);
    emit(EV_KEY, BTN_TOUCH, 1);
    emit(EV_KEY, BTN_TOOL_FINGER, 1);
    return emit(EV_SYN, SYN_REPORT, 0);
}

static int move_to(int x, int y) {
    x = map_x(x);
    y = map_y(y);
    emit(EV_ABS, ABS_MT_SLOT, 0);
    emit(EV_ABS, ABS_MT_POSITION_X, x);
    emit(EV_ABS, ABS_MT_POSITION_Y, y);
    emit(EV_ABS, ABS_X, x);
    emit(EV_ABS, ABS_Y, y);
    return emit(EV_SYN, SYN_REPORT, 0);
}

static int up(void) {
    emit(EV_ABS, ABS_MT_SLOT, 0);
    emit(EV_ABS, ABS_MT_TRACKING_ID, -1);
    emit(EV_KEY, BTN_TOUCH, 0);
    emit(EV_KEY, BTN_TOOL_FINGER, 0);
    return emit(EV_SYN, SYN_REPORT, 0);
}

static void reply_ok(void) { fputs("OK\n", stdout); fflush(stdout); }
static void reply_err(const char *m) {
    fputs("ERR ", stdout);
    fputs(m, stdout);
    fputc('\n', stdout);
    fflush(stdout);
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
    reply_ok();
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
    reply_ok();
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

        int x, y, x2, y2, d, w, h;
        if (strcmp(line, "PING") == 0) {
            reply_ok();
        } else if (strcmp(line, "QUIT") == 0) {
            close_device();
            reply_ok();
            break;
        } else if (sscanf(line, "CONFIG %d %d", &w, &h) == 2) {
            if (setup_device(w, h) == 0) reply_ok();
            else reply_err("uinput");
        } else if (sscanf(line, "TAP %d %d %d", &x, &y, &d) == 3) {
            if (!created && setup_device(screen_w, screen_h) != 0) {
                reply_err("uinput");
                continue;
            }
            do_tap(x, y, d);
        } else if (sscanf(line, "SWIPE %d %d %d %d %d", &x, &y, &x2, &y2, &d) == 5) {
            if (!created && setup_device(screen_w, screen_h) != 0) {
                reply_err("uinput");
                continue;
            }
            do_swipe(x, y, x2, y2, d);
        } else {
            reply_err("cmd");
        }
    }
    close_device();
    return 0;
}
