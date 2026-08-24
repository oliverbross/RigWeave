#include "rigweave/core.h"

#include <cassert>

int main() {
    for (int cycle = 0; cycle < 1000; ++cycle) {
        rw_feature_context *features = rw_feature_context_create();
        assert(features != nullptr);
        rw_feature_set_watchlist(features, "OM0RX\nVK9AA");
        rw_feature_context_destroy(features);
    }
    for (int cycle = 0; cycle < 500; ++cycle) {
        rw_context *radio = rw_context_create();
        assert(radio != nullptr);
        rw_context_destroy(radio);

        rw_panadapter_context *panadapter = rw_panadapter_context_create();
        assert(panadapter != nullptr);
        rw_panadapter_context_destroy(panadapter);
    }
    return 0;
}
