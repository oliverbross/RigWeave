# Android Scanner and Band Stacks

The scanner is receive-only and supports saved memories, bounded ranges, and FFT peak candidates. Start is explicit. Dwell and resume policy are visible. It stops on operator Stop, manual tune, radio disconnect, profile change, background, Global Stop, or controller close.

The scanner has no PTT, TUNE, drive, memory-write, or automatic-start path.

Band stacks persist only frequency, mode, filter, receiver identifier, and timestamp. Depth is configurable from 1 to 12. Record and cycle recall are explicit, require a connected radio, and never initiate a connection.
