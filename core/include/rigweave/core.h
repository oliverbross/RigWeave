#ifndef RIGWEAVE_CORE_H
#define RIGWEAVE_CORE_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct rw_context rw_context;
typedef struct rw_feature_context rw_feature_context;

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
    uint64_t vfo_b_hz;
    int connected;
    int transmitting;
    int meter;
    int swr_tenths;
    int rf_output_tenths;
    int af_gain;
    int rf_gain;
    int bandwidth_hz;
    int power_w;
    int preamp;
    int attenuator;
    int rit;
    int xit;
    int rx_vfo;
    int tx_vfo;
    int split;
    int agc_mode;
    int cwt;
    int monitor_level;
    int mic_gain;
    int keyer_speed;
    int if_shift_hz;
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

rw_feature_context *rw_feature_context_create(void);
void rw_feature_context_destroy(rw_feature_context *context);
int rw_feature_load_cty_text(rw_feature_context *context, const char *cty_text);
int rw_feature_set_watchlist(rw_feature_context *context, const char *watchlist_text);
int rw_feature_set_solar(rw_feature_context *context, float solar_flux, float a_index,
                         float kp_index, int64_t observed_epoch);
int rw_feature_ingest_cluster_line(rw_feature_context *context, const char *line,
                                   int64_t received_epoch);
int rw_feature_dx_snapshot_json(const rw_feature_context *context, char *output,
                                size_t output_size, int64_t now_epoch);
int rw_feature_add_worked_qso(rw_feature_context *context, const char *callsign,
                              const char *entity, const char *band, const char *mode,
                              const char *submode, int64_t epoch, int from_wavelog);
int rw_feature_worked_json(const rw_feature_context *context, char *output,
                           size_t output_size, const char *callsign, const char *entity,
                           const char *band, const char *mode, const char *submode,
                           int64_t now_epoch);
int rw_feature_propagation_json(char *output, size_t output_size,
                                const char *station_grid, const char *target_grid,
                                const char *band, int64_t epoch, float solar_flux,
                                float kp_index, int64_t solar_epoch,
                                unsigned observations, unsigned favorable_observations);

int rw_panadapter_push_pcm(rw_feature_context *context, const uint8_t *bytes, size_t length,
                           unsigned channels, unsigned subframe_bytes, unsigned bits);
size_t rw_panadapter_copy_bins(const rw_feature_context *context, uint8_t *output,
                               size_t output_size);
size_t rw_panadapter_copy_db_bins(const rw_feature_context *context, float *output,
                                  size_t output_count);
float rw_panadapter_peak_db(const rw_feature_context *context);
float rw_panadapter_i_rms_db(const rw_feature_context *context);
float rw_panadapter_q_rms_db(const rw_feature_context *context);
float rw_panadapter_iq_correlation(const rw_feature_context *context);

int rw_sync_action(int status_code, int network_error, int response_ambiguous);
uint32_t rw_sync_retry_delay(uint32_t attempt, uint32_t jitter_seed,
                             uint32_t retry_after, int has_retry_after);
int rw_wavelog_normalize_url(char *output, size_t output_size, const char *url);
int rw_wavelog_payload(char *output, size_t output_size, const char *api_key,
                       const char *station_profile_id, const char *adif);
int rw_wsjtx_parse_json(char *output, size_t output_size,
                        const uint8_t *datagram, size_t datagram_size);

const char *rw_core_version(void);

#ifdef __cplusplus
}
#endif

#endif
