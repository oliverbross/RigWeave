// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <stddef.h>
#include <stdint.h>
#ifdef __cplusplus
extern "C" {
#endif
typedef struct FlexContext rw_flex_context;
rw_flex_context *rw_flex_context_create(void);
void rw_flex_context_destroy(rw_flex_context *context);
int32_t rw_flex_context_feed(rw_flex_context *context, const uint8_t *bytes, size_t count);
int32_t rw_flex_state_json(const rw_flex_context *context, char *output, size_t capacity);
int32_t rw_flex_client_identity(const char *program, char *output, size_t capacity);
int32_t rw_flex_subscriptions(char *output, size_t capacity);
int32_t rw_flex_keepalive(char *output, size_t capacity);
int32_t rw_flex_frequency(uint32_t slice, uint64_t hz, char *output, size_t capacity);
int32_t rw_flex_mode(uint32_t slice, const char *mode, char *output, size_t capacity);
int32_t rw_flex_filter(const char *letter, int32_t low, int32_t high, char *output, size_t capacity);
int32_t rw_flex_parse_discovery(const uint8_t *bytes, size_t count, char *output, size_t capacity);
#ifdef __cplusplus
}
#endif
