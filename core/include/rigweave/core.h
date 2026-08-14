#ifndef RIGWEAVE_CORE_H
#define RIGWEAVE_CORE_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct rw_context rw_context;

typedef enum rw_command_class {
    RW_COMMAND_UNKNOWN = 0,
    RW_COMMAND_READ_ONLY = 1,
    RW_COMMAND_MUTATION = 2,
    RW_COMMAND_TRANSMIT = 3
} rw_command_class;

typedef struct rw_radio_state {
    char identity[24];
    char model[16];
    char mode[12];
    uint64_t vfo_a_hz;
    int connected;
    int transmitting;
    int meter;
    uint64_t revision;
} rw_radio_state;

rw_context *rw_context_create(void);
void rw_context_destroy(rw_context *context);
void rw_context_reset(rw_context *context);
int rw_context_feed(rw_context *context, const char *bytes, size_t length);
rw_radio_state rw_context_state(const rw_context *context);

rw_command_class rw_classify_command(const char *command);
size_t rw_startup_command_count(void);
int rw_qso_identity(char *output, size_t output_size, const char *callsign,
                    const char *utc_iso8601, uint64_t frequency_hz, const char *mode);
int rw_adif_serialize(char *output, size_t output_size, const char *identity,
                      const char *callsign, const char *date_yyyymmdd,
                      const char *time_hhmmss, uint64_t frequency_hz,
                      const char *mode, const char *rst_sent, const char *rst_received);

const char *rw_core_version(void);

#ifdef __cplusplus
}
#endif

#endif
