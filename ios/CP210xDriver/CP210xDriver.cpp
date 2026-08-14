#include <os/log.h>

#include <DriverKit/IOLib.h>
#include <DriverKit/IOUserServer.h>

#include "CP210xDriver.h"

namespace {
constexpr uint32_t kDefaultBaudRate = 38400;
}

kern_return_t
IMPL(CP210xDriver, Start)
{
	kern_return_t ret = Start(provider, SUPERDISPATCH);
	if (ret != kIOReturnSuccess) {
		os_log(OS_LOG_DEFAULT, "RigWeave CP210x Start super failed: 0x%08x", ret);
		return ret;
	}

	ret = RegisterService();
	os_log(OS_LOG_DEFAULT, "RigWeave CP210x matched VID 0x10C4 PID 0xEA60: 0x%08x", ret);
	return ret;
}

kern_return_t
IMPL(CP210xDriver, Stop)
{
	os_log(OS_LOG_DEFAULT, "RigWeave CP210x Stop");
	return Stop(provider, SUPERDISPATCH);
}

kern_return_t
IMPL(CP210xDriver, HwResetFIFO)
{
	os_log(OS_LOG_DEFAULT, "RigWeave CP210x reset FIFO tx=%{public}d rx=%{public}d", tx, rx);
	return kIOReturnSuccess;
}

kern_return_t
IMPL(CP210xDriver, HwSendBreak)
{
	os_log(OS_LOG_DEFAULT, "RigWeave CP210x break=%{public}d", sendBreak);
	return kIOReturnSuccess;
}

kern_return_t
IMPL(CP210xDriver, HwProgramUART)
{
	os_log(OS_LOG_DEFAULT, "RigWeave CP210x UART baud=%{public}u bits=%{public}u stops=%{public}u parity=%{public}u",
		   baudRate == 0 ? kDefaultBaudRate : baudRate, nDataBits, nHalfStopBits, parity);
	return kIOReturnSuccess;
}

kern_return_t
IMPL(CP210xDriver, HwProgramBaudRate)
{
	os_log(OS_LOG_DEFAULT, "RigWeave CP210x baud=%{public}u", baudRate == 0 ? kDefaultBaudRate : baudRate);
	return kIOReturnSuccess;
}

kern_return_t
IMPL(CP210xDriver, HwProgramMCR)
{
	os_log(OS_LOG_DEFAULT, "RigWeave CP210x DTR=%{public}d RTS=%{public}d", dtr, rts);
	return kIOReturnSuccess;
}

kern_return_t
IMPL(CP210xDriver, HwGetModemStatus)
{
	if (cts != nullptr) *cts = true;
	if (dsr != nullptr) *dsr = true;
	if (ri != nullptr) *ri = false;
	if (dcd != nullptr) *dcd = true;
	return kIOReturnSuccess;
}

kern_return_t
IMPL(CP210xDriver, HwProgramLatencyTimer)
{
	os_log(OS_LOG_DEFAULT, "RigWeave CP210x latency=%{public}u", latency);
	return kIOReturnSuccess;
}

kern_return_t
IMPL(CP210xDriver, HwProgramFlowControl)
{
	os_log(OS_LOG_DEFAULT, "RigWeave CP210x flow=%{public}u xon=%{public}u xoff=%{public}u", arg, xon, xoff);
	return kIOReturnSuccess;
}

void
CP210xDriver::handleRxPacket(uint8_t*& packet, uint32_t& size)
{
	os_log(OS_LOG_DEFAULT, "RigWeave CP210x RX bytes=%{public}u", size);
}

void
CP210xDriver::handleInterruptPacket(const uint8_t* packet, uint32_t size)
{
	os_log(OS_LOG_DEFAULT, "RigWeave CP210x interrupt bytes=%{public}u", size);
}
