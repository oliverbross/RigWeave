// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Oliver Bross

use crate::flexcat::{self, FlexFramer, FlexState};
use crate::flexdisc;
use std::ffi::{c_char, CStr};

#[repr(C)]
pub struct FlexContext {
    framer: FlexFramer,
    state: FlexState,
}

fn copy_text(value: &str, output: *mut c_char, capacity: usize) -> i32 {
    if output.is_null() || capacity == 0 {
        return -1;
    }
    let bytes = value.as_bytes();
    let count = bytes.len().min(capacity - 1);
    unsafe {
        std::ptr::copy_nonoverlapping(bytes.as_ptr(), output.cast(), count);
        *output.add(count) = 0;
    }
    count as i32
}

fn input(value: *const c_char) -> Option<String> {
    (!value.is_null()).then(|| unsafe { CStr::from_ptr(value).to_string_lossy().into_owned() })
}

fn json_string(value: &str) -> String {
    format!(
        "\"{}\"",
        value
            .replace('\\', "\\\\")
            .replace('"', "\\\"")
            .replace('\n', "\\n")
    )
}

fn state_json(state: &FlexState) -> String {
    let clients = state.clients.values().map(|v| format!("{{\"handle\":{},\"clientId\":{},\"program\":{},\"station\":{},\"connected\":{},\"gui\":{}}}", v.handle, json_string(&v.client_id), json_string(&v.program), json_string(&v.station), v.connected, v.is_gui)).collect::<Vec<_>>().join(",");
    let slices = state.slices.values().map(|v| format!("{{\"index\":{},\"letter\":{},\"inUse\":{},\"active\":{},\"tx\":{},\"clientHandle\":{},\"frequencyHz\":{},\"mode\":{},\"filterLowHz\":{},\"filterHighHz\":{},\"rxAntenna\":{}}}", v.index, json_string(&v.letter), v.in_use, v.active, v.tx, v.client_handle.unwrap_or(0), v.frequency_hz, json_string(&v.mode), v.filter_low_hz, v.filter_high_hz, json_string(&v.rx_antenna))).collect::<Vec<_>>().join(",");
    format!("{{\"connected\":{},\"handle\":{},\"version\":{},\"model\":{},\"nickname\":{},\"callsign\":{},\"serial\":{},\"firmware\":{},\"clients\":[{}],\"slices\":[{}]}}", state.connected, state.handle, json_string(&state.identity.version), json_string(&state.identity.model), json_string(&state.identity.nickname), json_string(&state.identity.callsign), json_string(&state.identity.serial), json_string(&state.identity.firmware), clients, slices)
}

fn discovery_json(record: &flexdisc::DiscoveryRecord) -> String {
    format!("{{\"model\":{},\"nickname\":{},\"ip\":{},\"port\":{},\"serial\":{},\"callsign\":{},\"version\":{},\"status\":{},\"guiClientHandles\":{},\"guiClientPrograms\":{},\"guiClientStations\":{}}}",
        json_string(&record.model), json_string(&record.nickname), json_string(&record.ip), record.port,
        json_string(&record.serial), json_string(&record.callsign), json_string(&record.version), json_string(&record.status),
        json_string(&record.gui_client_handles), json_string(&record.gui_client_programs), json_string(&record.gui_client_stations))
}

#[no_mangle]
pub extern "C" fn rw_flex_context_create() -> *mut FlexContext {
    Box::into_raw(Box::new(FlexContext {
        framer: FlexFramer::default(),
        state: FlexState::default(),
    }))
}
#[no_mangle]
pub unsafe extern "C" fn rw_flex_context_destroy(context: *mut FlexContext) {
    if !context.is_null() {
        drop(Box::from_raw(context));
    }
}
#[no_mangle]
pub unsafe extern "C" fn rw_flex_context_feed(
    context: *mut FlexContext,
    bytes: *const u8,
    count: usize,
) -> i32 {
    let Some(context) = context.as_mut() else {
        return -1;
    };
    if bytes.is_null() {
        return -1;
    }
    let messages = context
        .framer
        .push(std::slice::from_raw_parts(bytes, count));
    let applied = messages.len() as i32;
    for message in &messages {
        context.state.apply(message);
    }
    applied
}
#[no_mangle]
pub unsafe extern "C" fn rw_flex_state_json(
    context: *const FlexContext,
    output: *mut c_char,
    capacity: usize,
) -> i32 {
    context
        .as_ref()
        .map(|v| copy_text(&state_json(&v.state), output, capacity))
        .unwrap_or(-1)
}
#[no_mangle]
pub unsafe extern "C" fn rw_flex_client_identity(
    program: *const c_char,
    output: *mut c_char,
    capacity: usize,
) -> i32 {
    input(program)
        .and_then(|v| flexcat::client_identity_command(&v))
        .map(|v| copy_text(&v, output, capacity))
        .unwrap_or(-1)
}
#[no_mangle]
pub extern "C" fn rw_flex_subscriptions(output: *mut c_char, capacity: usize) -> i32 {
    copy_text(&flexcat::subscriptions().join("\n"), output, capacity)
}
#[no_mangle]
pub extern "C" fn rw_flex_keepalive(output: *mut c_char, capacity: usize) -> i32 {
    copy_text(flexcat::keepalive_command(), output, capacity)
}
#[no_mangle]
pub extern "C" fn rw_flex_frequency(
    slice: u32,
    hz: u64,
    output: *mut c_char,
    capacity: usize,
) -> i32 {
    flexcat::frequency_command(slice, hz)
        .map(|v| copy_text(&v, output, capacity))
        .unwrap_or(-1)
}
#[no_mangle]
pub unsafe extern "C" fn rw_flex_mode(
    slice: u32,
    mode: *const c_char,
    output: *mut c_char,
    capacity: usize,
) -> i32 {
    input(mode)
        .and_then(|v| flexcat::mode_command(slice, &v))
        .map(|v| copy_text(&v, output, capacity))
        .unwrap_or(-1)
}
#[no_mangle]
pub unsafe extern "C" fn rw_flex_filter(
    letter: *const c_char,
    low: i32,
    high: i32,
    output: *mut c_char,
    capacity: usize,
) -> i32 {
    input(letter)
        .and_then(|v| flexcat::filter_command(&v, low, high))
        .map(|v| copy_text(&v, output, capacity))
        .unwrap_or(-1)
}
#[no_mangle]
pub unsafe extern "C" fn rw_flex_parse_discovery(
    bytes: *const u8,
    count: usize,
    output: *mut c_char,
    capacity: usize,
) -> i32 {
    if bytes.is_null() {
        return -1;
    }
    flexdisc::parse_discovery(std::slice::from_raw_parts(bytes, count))
        .map(|v| copy_text(&discovery_json(&v), output, capacity))
        .unwrap_or(-1)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::ffi::CStr;

    #[test]
    fn c_abi_round_trip_owns_context_and_output() {
        let context = rw_flex_context_create();
        let input =
            b"V3.8.23\nH2ABC\nS0|slice 0 in_use=1 active=1 tx=0 RF_frequency=14.074 mode=DIGU\n";
        let applied = unsafe { rw_flex_context_feed(context, input.as_ptr(), input.len()) };
        assert_eq!(applied, 3);
        let mut output = [0_i8; 4096];
        assert!(unsafe { rw_flex_state_json(context, output.as_mut_ptr(), output.len()) } > 0);
        let text = unsafe { CStr::from_ptr(output.as_ptr()) }.to_string_lossy();
        assert!(text.contains("\"handle\":10940"));
        assert!(text.contains("\"frequencyHz\":14074000"));
        unsafe { rw_flex_context_destroy(context) };
    }
}
