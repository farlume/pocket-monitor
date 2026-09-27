#include <errno.h>
#include <jni.h>
#include <linux/usbdevice_fs.h>
#include <sys/ioctl.h>

/* Read the negotiated link speed; does not reset or reconfigure the device. */
JNIEXPORT jint JNICALL
Java_dev_icelum_pocketmonitor_UsbBulkReset_speed(JNIEnv *env, jobject self, jint fd) {
    (void) env;
    (void) self;
    if (fd < 0) return -EINVAL;
    int result;
    do {
        result = ioctl(fd, USBDEVFS_GET_SPEED, NULL);
    } while (result < 0 && errno == EINTR);
    return result < 0 ? -errno : result;
}

/* USBDEVFS_CLEAR_HALT resets both device halt and host endpoint toggle state.
 * The caller owns the interface and has already stopped all native transfers.
 */
JNIEXPORT jint JNICALL
Java_dev_icelum_pocketmonitor_UsbBulkReset_clearHalt(
        JNIEnv *env, jobject self, jint fd, jint endpoint) {
    (void) env;
    (void) self;
    if (fd < 0 || endpoint < 0x81 || endpoint > 0x8f) return -EINVAL;
    unsigned int address = (unsigned int) endpoint;
    int result;
    do {
        result = ioctl(fd, USBDEVFS_CLEAR_HALT, &address);
    } while (result < 0 && errno == EINTR);
    return result < 0 ? -errno : 0;
}
