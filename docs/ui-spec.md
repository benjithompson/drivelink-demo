# DriveLink Demo — UI Spec (Phase 1, updated in Phase 5)

Oct 2, 2026 · Ben Thompson

## Summary

This spec defines the look of each DriveLink screen. The layout follows a US OEM owner app (2026), called "the reference app" in these docs. The name, logo, font, vehicle art and copy are original.

The Phase 1 "lookbook" showed every screen with static data. Phase 5 replaced the lookbook screen by screen with real screens that use data from the virtual service. The layout, components and test tags stay the same.

Phase 5, stage 1 built Login, Home and the remote command flow (Remote Controls, Remote Start, PIN prompt). Stage 2 built Vehicle Status, Charging, Charging Schedule, Car Care, Schedule Service, Trips, Alerts, Maps, Menu, Profile, Settings and the Demo console setup tools. Only the Design Gallery is still a static page. It lists the tokens and components for the look review.

Screenshots are in [ui/screens/](ui/screens/). To capture them again, start an emulator and run:

```sh
scripts/screenshots.sh
```

## How to open a screen directly

`MainActivity` accepts launch extras. Screenshots and Perfecto tests use them to skip navigation. The activity uses `launchMode="singleTop"`. An `am start` on a running app delivers the extras to `onNewIntent`, and the app navigates without a restart.

| Extra | Values | Default |
| --- | --- | --- |
| `screen` | See the table below. | `home` |
| `scenario` | Any `X-Scenario` name from the catalog. The app stores it as the demo scenario, the same setting as the Demo console. | unchanged |
| `vehicle` | `aurora` (EV crossover), `solace` (gas sedan). The app stores the VIN as the selected vehicle. | unchanged |
| `dark` | `true`, `false` | system setting |

Values of `screen`:

| Value | Opens |
| --- | --- |
| `home` | Home tab |
| `carcare` | Car Care tab |
| `maps` | Maps tab |
| `menu` | Menu tab |
| `controls` | Remote Controls |
| `climate` | Remote Start |
| `pin` | PIN prompt for a Lock command |
| `charging` | Charging |
| `schedule` | Charging Schedule |
| `status` | Vehicle Status |
| `trips` | Trips |
| `service` | Schedule Service |
| `alerts` | Alerts |
| `profile` | Profile |
| `settings` | Settings |
| `console` | Demo console |
| `gallery` | Design Gallery |
| `login` | Login |

Rules:

- A launch request first closes the sub-screens that are open, then opens the screen. A request for `home` always shows Home.
- Without a session, every `screen` value except `console`, `gallery` and `login` shows Login. Sign in first, or keep the session from an earlier run.
- `screen=login` always shows Login. It does not sign out.
- `screen=pin` opens the PIN prompt for a Lock command.
- A new `scenario` clears the command card and reloads the vehicle data.
- The Phase 1 extra `command` is removed. The Design Gallery shows every command stage.
- The `scenario` extra changes the `X-Scenario` header that the app sends. The host that answers comes from the active endpoint profile (Demo console or `DemoConfigActivity`).

Example:

```sh
adb shell am start -n com.drivelink.demo/.MainActivity --es screen status --es scenario door-ajar
```

## Screen map

| Reference screen | DriveLink screen | Screenshot | Root test tag |
| --- | --- | --- | --- |
| Sign-in | Login | [p5-01](ui/screens/p5-01-login.png) | `screen_login` |
| PIN setup | Create a PIN | [p5-02](ui/screens/p5-02-pin-setup.png) | `screen_pin_setup` |
| Home tab | Home | [p5-03](ui/screens/p5-03-home.png), [p5-04 dark](ui/screens/p5-04-home-dark.png), [p5-05 gas](ui/screens/p5-05-home-ice.png) | `screen_home` |
| Command feedback | Home, command card | [p5-07](ui/screens/p5-07-command-waiting.png), [p5-08](ui/screens/p5-08-command-done.png), [p5-09](ui/screens/p5-09-command-failed.png) | `command_progress` |
| Controls | Remote Controls | [p5-10](ui/screens/p5-10-controls.png) | `screen_controls` |
| Remote start presets | Remote Start | [p5-11](ui/screens/p5-11-climate.png) | `screen_climate` |
| PIN prompt | Enter PIN | [p5-06](ui/screens/p5-06-pin.png) | `screen_pin` |
| Charging sheet | Charging | [10](ui/screens/10-charging.png) (Phase 1), [p5-17](ui/screens/p5-17-charging.png) | `screen_charging` |
| Charging schedule | Charging Schedule | [p5-18](ui/screens/p5-18-charge-schedule.png) | `screen_charge_schedule` |
| Vehicle Status | Vehicle Status | [11](ui/screens/11-status-door-ajar.png) (Phase 1), [p5-15 quick](ui/screens/p5-15-status-quick.png), [p5-16 full](ui/screens/p5-16-status-full.png), [p5-28 dark](ui/screens/p5-28-status-dark.png), [p5-31 offline](ui/screens/p5-31-status-offline.png) | `screen_status` |
| Car Care tab | Car Care | [12](ui/screens/12-carcare.png) (Phase 1), [p5-19](ui/screens/p5-19-carcare.png), [p5-30 dark](ui/screens/p5-30-carcare-dark.png) | `screen_carcare` |
| Schedule service | Schedule Service | [p5-20 form](ui/screens/p5-20-service-request.png), [p5-20b confirmation](ui/screens/p5-20b-service-confirmation.png) | `screen_service_request` |
| Trips | Trips | [p5-21](ui/screens/p5-21-trips.png), [p5-33 rate-limited](ui/screens/p5-33-trips-rate-limited.png) | `screen_trips` |
| Alerts | Alerts | [p5-22](ui/screens/p5-22-alerts.png) | `screen_alerts` |
| Maps tab | Maps | [13](ui/screens/13-maps.png) (Phase 1), [p5-23](ui/screens/p5-23-maps.png), [p5-29 dark](ui/screens/p5-29-maps-dark.png) | `screen_maps` |
| Menu tab | Menu | [14](ui/screens/14-menu.png) (Phase 1), [p5-24](ui/screens/p5-24-menu.png) | `screen_menu` |
| Profile | Profile | (no screenshot) | `screen_profile` |
| Settings | Settings | [p5-25](ui/screens/p5-25-settings.png) | `screen_settings` |
| (none) | Demo console, setup tools | [p5-26](ui/screens/p5-26-console-tools.png) | `screen_console` |
| (none) | Design Gallery (debug) | [15](ui/screens/15-gallery.png) | `screen_gallery` |

## App frame

- **Top bar, Home tab:** navy bar. The DriveLink mark is on the left. The alerts bell (with a cyan count badge) and the messages icon are on the right.
- **Top bar, Car Care and Menu tabs:** navy bar with a centered title. No icons.
- **Top bar, sub-screens:** navy bar with a back chevron and a centered title.
- **Maps tab:** no top bar. The map goes under the status bar, and the status-bar icons are dark.
- **Bottom bar:** four tabs: Home, Car Care, Maps, Menu. The active tab is navy and bold; the other tabs are gray. Test tags: `tab_home`, `tab_carcare`, `tab_maps`, `tab_menu`.
- **Scenario chip:** when the scenario is not `default`, a dark chip at the bottom left shows the scenario name and the host of the active profile. A tap opens the Demo console. Test tag: `scenario_chip`.
- **Navigation:** Navigation Compose with type-safe routes. Login and PIN setup have no bars. The bottom tabs keep Home at the bottom of the back stack. A sub-screen has a back chevron. The auth gate returns to Login when the session ends.

## Screens

### Login and PIN setup

| Part | Content | Test tag |
| --- | --- | --- |
| Login | Email and password, prefilled with the demo credentials from `api/openapi.yaml`. Button "Sign in" with a progress indicator. | `screen_login`, `login_email`, `login_password`, `login_submit`, `login_loading` |
| Notice | Why the user is here again, for example "Your session has expired. Sign in again." | `login_notice` |
| Error | Message (invalid credentials, network, server) and the correlation id. | `login_error`, `login_correlation_id` |
| PIN setup | Shown after the first sign-in only. The user types a 4-digit PIN, then types it again. The PIN pad uses the keys `pin_key_0` to `pin_key_9` and `pin_key_delete`. A mismatch shows an error and starts again. | `screen_pin_setup`, `pin_setup_hint`, `pin_error` |

The device stores a salted hash of the PIN in the settings DataStore. Sign out (Menu, `menu_sign_out`) clears the session and the vehicle data. It keeps the PIN, so the next sign-in skips PIN setup.

### Home

| Part | Content | Test tag |
| --- | --- | --- |
| Hero | Model year and name with a caret (vehicle switcher), trim below, vehicle art on a light-blue gradient | `vehicle_switcher` |
| Stats | Two centered numbers: battery % (or fuel %) and estimated range (mi or km, from the user's units) | `stat_level`, `stat_range` |
| Chip | EV, from the charging state: "Charging 42 min left", "Charge complete", "Not plugged in" or "Charge Schedule On". Gas: "Doors Locked" or "Doors Unlocked". | `status_chip` |
| Tiles | Lock, Climate, Charge (EV) or Fuel (gas), Controls. Active tile is navy; busy tile shows a spinner | `tile_lock`, `tile_climate`, `tile_charge`, `tile_fuel`, `tile_controls` |
| Command card | Shows while a command runs and for 2.5 s after it succeeds. A failure stays until the user dismisses it. | `command_progress`, `command_stage`, `command_retry`, `command_dismiss` |
| Rows | Location (place name), Vehicle Status, Vehicle Health, Trips (last trip) | `row_location`, `row_status`, `row_health`, `row_trips` |
| Bell | Count of unread alerts | `topbar_alerts` |
| Refresh | Pull down to reload | `home_refresh` |

Home ends with a 64 dp spacer, so the scenario chip does not cover the last row.

Tile behavior:

- After a Lock or Unlock command succeeds, the Lock tile shows the commanded state until a later status shows a different value (D-30). The Climate tile and the charging chip work the same way. The overrides end on a vehicle switch, a profile or scenario change, and sign out.
- `tile_lock` shows "Locked" (navy) or "Unlocked". A tap sends LOCK when the vehicle is unlocked and UNLOCK when it is locked.
- `tile_climate` opens Remote Start. `tile_charge` opens Charging. `tile_fuel` opens Vehicle Status. `tile_controls` opens Remote Controls.
- Every tile has a content description (its label) and a state description ("On", "Off", "In progress").

Scenario states on Home:

| Scenario | What the screen shows |
| --- | --- |
| `default` | Level, range, chip and rows from the status of the selected vehicle |
| `vehicle-offline` | Stale banner with the data age (`banner_stale`). Lock fails with "The vehicle is offline." |
| `low-battery` | Level 8 % in red, range 19 mi, warning banner (`banner_warning`) |
| `door-ajar` | Lock tile shows "Unlocked". Vehicle Status row shows "Door Open" in red. |
| `tire-low` | Vehicle Health row shows "Tire Pressure Low" in red |
| `server-error`, `rate-limited`, `bad-payload` | Error card (a rate-limit error adds "Try again in N seconds.") (`error_state`) with a message, the correlation id (`error_correlation_id`) and Retry (`error_retry`). With data on screen, a pull-to-refresh failure shows an error banner with Retry instead (`banner_error`, `banner_action`). |
| `auth-expired` | The token refresh fails, the session ends, and the app opens Login with a notice. |
| `slow-vehicle`, `vehicle-asleep`, `command-fails`, `command-timeout` | Change the command card (see the next section) |

### Vehicle picker

The caret next to the vehicle name opens a bottom sheet (`vehicle_picker`) with one row for each vehicle (`vehicle_option_<vin>`). A row shows a vehicle thumbnail, the name, the trim and the last six characters of the VIN (`vehicle_option_<vin>_vin`, "VIN …000101"). A check mark (`vehicle_option_<vin>_selected`) marks the selected vehicle. A tap selects the vehicle, stores its VIN and reloads the data. The sheet root exposes test tags as resource ids.

### Remote command flow

1. The user taps a command (Lock tile, or a tile on Remote Controls, or "Start Vehicle" on Remote Start).
2. The PIN screen opens. Four digits complete the PIN. Keys: `pin_key_0` to `pin_key_9`, `pin_key_delete`. The app checks the PIN against the stored PIN on the device. A wrong PIN shows "The PIN is incorrect." (`pin_error`) and sends nothing.
3. The app returns to Home. The app sends the command with the PIN in the body (D-07) and polls it. The command card shows the stages in order: "Sending command…", "Waiting for vehicle…", then "Done" (green) or a red failure.
4. `vehicle-asleep` adds the stage "Waking vehicle…".
5. After "Done" the app reloads the vehicle status.

Failure texts:

| Result | Card text |
| --- | --- |
| Vehicle rejects the command | "Command failed · " and the reason, for example "A door is open. Close all doors and try again." |
| No result in 60 s | "Vehicle did not respond" |
| Send or poll error | The error message, for example "The vehicle is offline. Try again when it has a connection." |

Retry (`command_retry`) sends the same command again with the same PIN. The close button (`command_dismiss`) removes the card.

### Remote Controls

- Section "Doors": Lock, Unlock, Horn & Lights, Lights (`cmd_lock`, `cmd_unlock`, `cmd_horn_lights`, `cmd_lights`).
- Section "Engine and climate": Start (opens Remote Start), Stop (`cmd_start`, `cmd_stop`).

### Remote Start

- Preset chips from the vehicle: Summer, Winter, Preset 3, Preset 4 (`preset_0` to `preset_3`). A preset fills the form. A change to the form clears the chip.
- Temperature stepper: OFF, LO, 62–82°, HI (`climate_temperature`). When the unit of the user is °C, the range is 17–28° (D-13). The app converts the value to °F before it sends it.
- Switches: Front Defrost, Rear Defrost, Heated Steering Wheel (`toggle_front_defrost`, and so on).
- Heated Seats is a level selector, not a switch (D-10): Off, 1, 2, 3 (`climate_heated_seats`, options `climate_heated_seats_0` to `climate_heated_seats_3`).
- Duration slider: 1–10 min (`climate_duration`). The slider has a round thumb (D-34). The default Material 3 thumb is a thin bar that looked like a stray line at 10 min.
- Button "Start Vehicle" (`climate_start`) opens the PIN screen. After the PIN, the app sends START with `ClimateParams` (mode, temperature, defrost options, heated seat level, duration, preset id).

### Vehicle Status

The screen reads the shared vehicle state. It works for the EV and for the gas car.

- Two tabs: Quick View (`tab_quick_view`) and Full List (`tab_full_list`). Quick View is the first tab. Each tab starts at the top.
- Pull down to reload (`status_refresh`). The reload loads the full vehicle state, the same as on Home.
- The top of the screen can show two banners:
  - `banner_stale` when the connectivity is `OFFLINE`. The text names the age of the data.
  - `banner_error` with Retry (`banner_action`) when a reload fails and old data is on screen.
- Without data, the screen shows a progress indicator (`status_loading`) or the error card (`error_state`). The card shows the message, the correlation id and Retry. `AppError.Unauthorized` ends the session. The app opens Login with a notice.
- Quick View shows a gray diagram (`status_diagram`):
  - The pills are Hood, four doors and Trunk. An open item shows a red pill and a red line on the outline.
  - The fan icon is blue when the climate is on and gray when it is off.
  - The lock icon is blue when locked and red when unlocked.
- Under the diagram, Quick View shows these rows: Location, Vehicle (On or Off), Tire Pressure, Climate, Battery or Fuel (percent and range), Odometer.
  - A card with the four tire pressures follows the Tire Pressure row. A wheel in `lowWheels` is red.
  - The text "Updated <time ago>" is at the end (`status_updated`).
- Full List shows every item as a row: Location, Vehicle, Doors (lock), Hood, four doors, Trunk, four windows (only when the payload has them), Climate, Tire Pressure, four tires, Battery or Fuel, Est. Range, Charging (EV only), Odometer, Oil life (gas car), 12V battery.
- Distances use the unit of the user (D-13). Tire pressure stays in psi.
- A 64 dp spacer at the end of the scroll area keeps the scenario chip off the last row.

| Test tag | Element |
| --- | --- |
| `screen_status` | Root of the screen |
| `tab_quick_view`, `tab_full_list` | The two tabs |
| `status_refresh` | Pull-to-refresh container |
| `status_loading` | Progress indicator while the first load runs |
| `status_diagram` | Gray diagram area |
| `pill_hood`, `pill_trunk` | Hood and trunk pills |
| `pill_door_fl`, `pill_door_fr`, `pill_door_rl`, `pill_door_rr` | Door pills |
| `status_lock_icon`, `status_climate_icon` | Icons in the diagram. The content description is "Locked", "Unlocked", "Climate on" or "Climate off". |
| `status_row_<key>` | One row. Keys in Quick View: `location`, `vehicle`, `tires`, `climate`, `level`, `odometer`. |
| `status_row_<key>_value` | The value text of that row |
| `status_tires`, `tire_fl`, `tire_fr`, `tire_rl`, `tire_rr` | Tire card and its four cells |
| `tire_<position>_value` | Tire pressure text, for example "38 psi" |
| `status_full_list` | Container of the Full List rows |
| `status_updated` | "Updated 2 hr ago" |

Extra row keys in Full List: `lock`, `hood`, `door_fl`, `door_fr`, `door_rl`, `door_rr`, `trunk`, `window_fl`, `window_fr`, `window_rl`, `window_rr`, `tire_fl`, `tire_fr`, `tire_rl`, `tire_rr`, `range`, `charging`, `oil`, `aux12v`.

### Charging

- The screen is for the EV. For the gas car it shows "Charging is not available for this vehicle" (`charging_unavailable`, `charging_unavailable_text`). It sends no request to `charge-settings`.
- The green header shows the title "Charging", the state (`charge_state`: "AC charging", "DC fast charging", "Charge complete", "Plugged in, not charging" or "Not plugged in"), "Last updated <time ago>" (`charge_updated`) and the battery bar (`charge_bar`). The bar marker shows the AC limit, or the DC limit when a DC charger is in use. The marker follows the sliders.
- The stat grid has six cells. Cells that have no value show "--". Rate and time remaining show "--" when the car does not charge.
- The button below the grid is "Stop charge" (`charge_stop`) while the car charges. Otherwise it is "Start charge" (`charge_start`). "Start charge" is off when the car is not plugged in.
  - A tap sets the pending command CHARGE_STOP or CHARGE_START and opens the PIN screen. The command card shows on Home.
  - After the command succeeds, the screen shows the commanded state (D-30).
- "Find a charging station" (`charge_find_station`) opens the Maps tab.
- Rows: Charging Limits (information only), Charging Schedule (opens the schedule screen), Departure Climate (opens the schedule screen).
- The AC and DC sliders go from 50 to 100 percent in steps of 10.
  - The label above the slider shows the value (`limit_ac_value`, `limit_dc_value`).
  - The screen saves 500 ms after the last move (D-32). The line `limits_status` shows "Saving…", "Saved" or an error text with the correlation id. After an error, the sliders go back to the saved values.
  - When the settings do not load, a warning banner with Retry replaces the sliders.
- The screen has the same banners, error card and pull-to-refresh as Vehicle Status (`charging_refresh`, `charging_loading`).

| Test tag | Element |
| --- | --- |
| `screen_charging` | Root of the scroll content |
| `charging_refresh` | Pull-to-refresh container |
| `charging_loading` | Progress indicator while the first load runs |
| `charge_state`, `charge_updated`, `charge_bar` | Header texts and battery bar |
| `charge_stats` | The stat grid |
| `stat_battery`, `stat_range`, `stat_time`, `stat_rate`, `stat_cost`, `stat_energy` | The six cells |
| `stat_<key>_value` | The value text of a cell |
| `charge_start`, `charge_stop` | Start and stop button (only one shows) |
| `charge_find_station` | Find a charging station |
| `row_charge_limits`, `row_charge_schedule`, `row_departure` | The three rows |
| `row_charge_limits_value`, `row_charge_schedule_value`, `row_departure_value` | Value text. Examples: "AC 80% · DC 90%", "Active", "7:30 AM". |
| `limit_ac`, `limit_dc` | The sliders |
| `limit_ac_value`, `limit_dc_value` | The percent labels |
| `limits_status` | Save status text |
| `limits_loading` | Progress indicator while the settings load |
| `charging_unavailable`, `charging_unavailable_text` | Notice for the gas car |

### Charging Schedule

- The screen lists the schedules and the departure from the charge settings.
- Each schedule is a card (`schedule_<i>`) with a switch (`schedule_enabled_<i>`), a start time (`schedule_start_<i>`), an end time (`schedule_end_<i>`) and seven day buttons (`schedule_day_<i>_MON` to `schedule_day_<i>_SUN`).
- The departure card (`departure`) has a switch (`departure_enabled`), a time (`departure_time`) and seven day buttons (`departure_day_MON` to `departure_day_SUN`). The climate of the departure stays as the server sent it.
- A tap on a time opens a time picker dialog with OK (`time_ok`) and Cancel (`time_cancel`). The dialog uses the colors of the DriveLink theme.
- "Save" (`schedule_save`) sends the whole settings object with PUT, then closes the screen.
  - A failed save keeps the form and shows an error banner with the correlation id and Retry.
  - Save is off while a schedule or the departure is on and has no day. The text `schedule_invalid` explains this.
- After a save, the Charging screen shows the new values at once.
- A 64 dp spacer at the end of the scroll area keeps the scenario chip off the last element.

| Test tag | Element |
| --- | --- |
| `screen_charge_schedule` | Root of the screen |
| `schedule_loading`, `schedule_empty`, `schedule_unavailable`, `schedule_invalid` | State texts and indicator |
| `schedule_<i>`, `departure` | The cards |
| `schedule_enabled_<i>`, `departure_enabled` | The switches |
| `schedule_start_<i>`, `schedule_end_<i>`, `departure_time` | The time fields |
| `schedule_start_<i>_value`, `schedule_end_<i>_value`, `departure_time_value` | Time text, for example "11:00 PM" |
| `schedule_day_<i>_<DAY>`, `departure_day_<DAY>` | Day buttons. The state description is "On" or "Off". |
| `time_ok`, `time_cancel` | Time picker buttons |
| `schedule_save` | Save |

### Car Care

The tab loads `getMaintenance` for the selected vehicle (D-09). The app shell draws the title "Car Care". Four screens share these rules (Car Care, Schedule Service, Trips, Alerts):

- The screen loads its data for the selected vehicle. A vehicle switch, a new endpoint profile or a new scenario loads the data again (D-38).
- Pull down reloads the data.
- A load error with no data on screen shows the error card: a message, the correlation id (`error_correlation_id`) and Retry (`error_retry`).
- A load error with data on screen keeps the data and shows an error banner (`banner_error`) with Retry (`banner_action`).
- A rate-limit error adds the wait: "Too many requests. Try again in 30 seconds."
- An `Unauthorized` error ends the session. The app opens Login with the notice.
- Each scrolling screen ends with a 64 dp spacer, so the scenario chip covers no text.

| Part | Behavior |
| --- | --- |
| Headline | "Everything Looks Good" in green. One or more overdue items give "Service Overdue" (red icon and text). One or more due items give "Service Due". An open recall alone gives "Open Recall". The worst case wins (D-39). |
| Summary | Shows under a warning headline, for example "2 items due, 1 open recall". |
| Figures card | Odometer, Last Service Completed (distance and date), Next Service Due At (distance and date). Dates use `MM/dd/yy`. A missing value shows "—". |
| Interval | "Based on your driving habits and conditions, your service interval is 7,500 miles." The unit follows `User.units`. |
| Maintenance items | One row for each item: name, due text ("Due at 19,500mi" or "Due by 12/01/26") and a status tag (Upcoming, Due, Overdue). |
| Recalls | The section "Recalls". One card for each recall: title, status tag (Open or Remedied), campaign, issue date, description. "No open recalls" shows when the list is empty. |
| Schedule Service | Opens Schedule Service. |
| Service center card | Name, address, phone, distance, hours, Open or Closed (from the field `openNow`). A tap on the phone row opens the dialer (`ACTION_DIAL`, `tel:`). The app does not start the call. |

| Tag | Element |
| --- | --- |
| `screen_carcare` | Scroll content |
| `carcare_refresh` | Pull-to-refresh container |
| `carcare_loading` | First-load spinner |
| `carcare_headline`, `carcare_summary` | Headline text and summary text |
| `carcare_odometer` | Odometer value |
| `carcare_last_miles`, `carcare_last_date` | Last service values |
| `carcare_next_miles`, `carcare_next_date` | Next service values |
| `carcare_interval` | Interval sentence |
| `maint_item_<id>`, `maint_status_<id>` | Item row, item status tag |
| `recalls_section`, `recalls_none` | Section title, empty text |
| `recall_<id>`, `recall_status_<id>` | Recall card, recall status tag |
| `schedule_service` | Button |
| `service_center`, `service_center_name`, `service_center_address` | Card, name, address |
| `service_center_phone`, `service_center_distance`, `service_center_hours`, `service_center_open` | Phone row, distance, hours, Open or Closed |
| `error_state`, `error_message`, `error_correlation_id`, `error_retry` | Error card |
| `banner_error`, `banner_action` | Error banner with data on screen |

### Schedule Service

The sub-screen loads `getMaintenance` for the preferred service center and the items. The app shell draws the title "Schedule Service".

Form fields:

1. Service center. Read-only card with name and address (`service_form_center`). Without a preferred center, the form shows `service_no_center` and Submit stays off.
2. Preferred date. A tap opens the Material 3 date picker (`service_date_picker`). The picker allows tomorrow to 30 days ahead. Other days are off.
3. Preferred time (optional). Three chips: Morning (8-11 am), Midday (11 am-2 pm), Afternoon (2-5 pm). A second tap on the chosen chip clears it.
4. Services. One checkbox for each maintenance item (due and overdue items first), then "Other". No box is checked at the start (D-36).
5. Notes (optional). At most 400 characters.

Submit ("Request Service", `service_submit`):

- With no date, the form shows "Choose a date." (`service_date_error`).
- With no service, the form shows "Choose at least one service." (`service_services_error`).
- With a valid form, the app sends `createServiceRequest`. The button shows "Sending request…" and stays off while the request runs.
- On 201, the screen shows the confirmation (`service_confirmation`): request id, date, service center and status. Done (`service_done`) returns to Car Care.
- On an error, the form stays as it was. A box shows the message and the correlation id.

The request has the fields `serviceCenterId`, `preferredDate` (`yyyy-MM-dd`), `itemIds` (null when only "Other" is checked) and `notes`. The time slot and "Other" go into `notes` (D-35).

| Tag | Element |
| --- | --- |
| `screen_service_request` | Scroll content |
| `service_loading` | Spinner while the center loads |
| `service_form_center`, `service_no_center` | Center card, missing-center text |
| `service_date`, `service_date_error` | Date row, date error |
| `service_date_picker`, `service_date_confirm` | Date dialog, OK button |
| `slot_morning`, `slot_midday`, `slot_afternoon` | Time chips |
| `service_option_<itemId>`, `service_option_other` | Service checkboxes |
| `service_services_error` | Services error |
| `service_notes` | Notes field |
| `service_error`, `service_error_message`, `service_error_correlation_id` | Submit error box |
| `service_submit` | Submit button |
| `service_confirmation`, `service_confirmation_title` | Confirmation block, title |
| `service_request_id`, `service_confirmation_date`, `service_confirmation_center`, `service_confirmation_status` | Confirmation values |
| `service_done` | Done button |
| `error_state`, `error_retry` | Error card when the center does not load |

### Trips

The sub-screen loads `listTrips(vin, 20)` for the selected vehicle. The app shell draws the title "Trips".

| Part | Behavior |
| --- | --- |
| Summary card | Trip count ("3 trips") and total distance ("49.4 mi"). |
| Day groups | A title for each day in the device time zone: "Today", "Yesterday", or "Thu, Oct 1". Newest day first. |
| Trip row | Start and end time ("2:05 PM – 2:31 PM"), duration ("26 min"), distance ("12.4 mi"), efficiency. |
| Efficiency | EV: "3.9 mi/kWh" (km user: "6.3 km/kWh"). Gas car: "34.2 MPG" (km user: "6.9 L/100 km"). |
| Empty | "No trips yet". The summary shows "0 trips". |

The trip schema has no start place and no end place. The rows show no from/to labels.

| Tag | Element |
| --- | --- |
| `screen_trips`, `trips_refresh`, `trips_loading`, `trips_empty` | Content, refresh container, spinner, empty text |
| `trips_summary`, `trips_summary_count`, `trips_summary_distance` | Summary card and its values |
| `trip_day_<yyyy-MM-dd>` | Day title |
| `trip_row_<id>` | Trip row |
| `trip_time_<id>`, `trip_duration_<id>`, `trip_distance_<id>`, `trip_efficiency_<id>` | Row values |
| `error_state`, `error_message`, `error_correlation_id`, `error_retry` | Error card |

### Alerts

The sub-screen loads `listAlerts` for all vehicles of the account (D-37). The app shell draws the title "Alerts".

| Part | Behavior |
| --- | --- |
| Summary | "2 unread", "1 unread" or "All caught up". |
| Mark all read | A text button, shown while an alert is unread. It marks each unread alert, one request after the other. |
| Row | Severity icon, title (bold while unread), body, time ago ("3 hr ago"), vehicle name. Info is cyan, Warning is amber, Critical is red. |
| Unread dot | A cyan dot at the right edge. It is absent after the alert is read. |
| Tap | A tap on an unread alert marks it read at once (optimistic). On an error, the row becomes unread again. A banner shows the message and the correlation id. The banner has Dismiss. |
| Home bell | After the list loads, and after each mark, the app calls `GarageRepository.setUnreadAlerts(count)`. The bell on Home shows the new count. |
| Scenarios | `low-battery`, `door-ajar` and `tire-low` each return one unread Warning alert. |
| Empty | "No alerts". |

| Tag | Element |
| --- | --- |
| `screen_alerts`, `alerts_refresh`, `alerts_loading`, `alerts_empty` | Content, refresh container, spinner, empty text |
| `alerts_summary`, `alerts_mark_all` | Summary text, Mark all read button |
| `alert_row_<id>` | Alert row (clickable) |
| `alert_unread_<id>` | Unread dot. Present only while the alert is unread. |
| `alert_title_<id>`, `alert_body_<id>`, `alert_time_<id>`, `alert_vehicle_<id>`, `alert_severity_<id>` | Row values and icon |
| `banner_error`, `banner_action` | Load error or mark-read error banner |
| `error_state`, `error_message`, `error_correlation_id`, `error_retry` | Error card |

### Maps

The map fills the screen and goes under the status bar. The tab has no top bar. The status-bar icons are dark.

| Part | Behavior | Test tag |
| --- | --- | --- |
| Screen | The root of the Maps tab. | `screen_maps` |
| Map | OpenStreetMap tiles (osmdroid, D-41). The user can pan and zoom. In dark theme the tiles are inverted. A small text shows "© OpenStreetMap contributors" above the sheet. Without internet the tiles stay blank. The marker, the card and the data still work. | `map_attribution` |
| Vehicle marker | A round badge with a car icon at the vehicle position. It follows the map when the user pans or zooms. Its content description is "<year> <model> location". | `map_vehicle_marker` |
| Right rail | White square buttons. Route, My location, Chargers and Service centers show a short message "... is not available in the demo." Vehicle centers the map on the vehicle. Refresh loads the location again. | `map_route`, `map_recenter`, `map_my_location`, `map_chargers`, `map_service`, `map_refresh` |
| Bottom sheet | A "Navigate" search field (a stub), five shortcut circles (stubs), and the "My Vehicle" card. | `map_search`, `map_shortcut_chargers`, `map_shortcut_service`, `map_shortcut_parking`, `map_shortcut_home`, `map_shortcut_work` |
| Vehicle card | Vehicle name, address, distance, accuracy and "Updated <time ago>". | `map_vehicle_card`, `map_vehicle_name`, `map_vehicle_address`, `map_vehicle_distance`, `map_vehicle_accuracy`, `map_vehicle_updated` |
| Loading | A progress ring in the card place. | `map_loading` |
| Error | The error card shows the message, the correlation id and Retry. A failed refresh with data on screen shows a banner with Retry above the card. | `error_state`, `error_message`, `error_correlation_id`, `error_retry`, `banner_error`, `banner_action` |

Rules:

- The distance is measured from a fixed demo origin (D-40). The unit follows the units of the user (mi or km, D-13). The text is "0.3 mi away".
- The accuracy is always in meters, for example "±8 m" (D-44).
- The address is `Location.address`. Without it, the card shows the locality, then "Address unavailable".
- A new vehicle on Home, a new scenario or a new profile loads the location again.
- `rate-limited` shows "Too many requests. Try again in 30 seconds." when the server sent Retry-After.
- `auth-expired` ends the session. The app opens Login with a notice.
- When the scenario chip is on screen, the sheet keeps 56 dp of free space at the bottom left. The chip does not cover the card text.

### Menu

The app shell draws the title "Menu".

| Part | Behavior | Test tag |
| --- | --- | --- |
| Screen | The root of the Menu tab. | `screen_menu` |
| Header | Avatar with initials, the account name and the account email. A tap opens Profile. Before the account loads, the email of the session shows. | `menu_header`, `menu_user_name`, `menu_user_email` |
| Account | Profile and Settings. | `menu_profile`, `menu_settings` |
| Vehicle | Digital Key, Roadside Assistance and Subscription. A tap opens a dialog "Not available in the demo." (D-11). | `menu_digital_key`, `menu_roadside`, `menu_subscription`, `menu_stub_dialog`, `menu_stub_ok` |
| Demo | Demo Console, Design Gallery, About. About opens a dialog with the app version. | `menu_demo_console`, `menu_gallery`, `menu_about`, `menu_about_dialog`, `menu_about_ok` |
| Sign out | A tap opens a confirmation dialog (D-44). "Sign out" clears the session and the vehicle data and keeps the PIN. "Cancel" closes the dialog. | `menu_sign_out`, `sign_out_confirm`, `sign_out_confirm_yes`, `sign_out_cancel` |

The header merges its texts into one clickable item. A test that reads `menu_user_name` must use the unmerged semantics tree.

### Profile

The app shell draws the top bar and the back arrow.

| Part | Behavior | Test tag |
| --- | --- | --- |
| Screen | The root of the screen. | `screen_profile` |
| Loading | A progress ring while `getMe` runs. | `profile_loading` |
| Account | Avatar, name, email, account id and units (for example "Miles (mi), Fahrenheit (°F)"). The API has no phone number, so the screen shows none. | `profile_avatar`, `profile_name`, `profile_email`, `profile_user_id`, `profile_units` |
| Vehicle | The selected vehicle: model, trim and VIN. | `profile_vehicle`, `profile_vehicle_vin` |
| Error | The error card with the message, the correlation id and Retry. | `error_state`, `error_message`, `error_correlation_id`, `error_retry` |

### Settings

The app shell draws the top bar and the back arrow.

| Part | Behavior | Test tag |
| --- | --- | --- |
| Screen | The root of the screen. | `screen_settings` |
| Theme | Three options: System, Light, Dark. The choice is saved on the device and applies at once (D-42). | `settings_theme`, `settings_theme_system`, `settings_theme_light`, `settings_theme_dark` |
| Units | Read-only text from the account (D-13). Before the account loads, the defaults show with a note. | `settings_units` |
| Change PIN | A full-screen PIN pad. Step 1: the current PIN. Step 2: the new PIN. Step 3: the new PIN again. A wrong current PIN, a mismatch or the same PIN shows an error. After success a banner shows "Your PIN was changed." | `settings_change_pin`, `settings_pin_dialog`, `settings_pin_step_current`, `settings_pin_step_new`, `settings_pin_step_confirm`, `settings_pin_cancel`, `settings_pin_changed`, `pin_key_0` to `pin_key_9`, `pin_key_delete`, `pin_dots`, `pin_error` |
| Notifications | Three switches: Vehicle alerts, Charging updates, Service reminders. The app stores them on the device. It sends no push messages. | `settings_notifications`, `toggle_vehicle_alerts`, `toggle_charging_updates`, `toggle_service_reminders` |
| Version | The app version. | `settings_version` |

The launch extra `dark` overrides the theme setting until the user picks a theme in Settings.

### Demo console

The existing `console.*` and `inspector.*` tags are in the section "Demo console test tags (Phase 4)". The setup tools are new in stage 2. They sit in the section "Setup tools". The section is closed at first. A tap on the header opens it. It stays open when the editor or the request detail closes.

| Part | Behavior | Test tag |
| --- | --- | --- |
| Section | Header of the setup tools. | `console.tools` |
| Add profile | Opens the profile editor with empty fields. A new profile is stored but not activated. | `console.profile.add` |
| Edit profile | Opens the editor for the active profile. | `console.profile.edit` |
| Editor | Fields: name, base URL, API key header, API key value, trust user certificates, and one URL override for each group (Auth, Vehicle, Alerts). The screen never shows a stored API key value. An empty value keeps the stored key. The switch "Clear stored key" removes it. A built-in profile keeps its name. The cloud and Local profiles keep their base URL. | `console.profile.editor`, `console.profile.field.name`, `console.profile.field.baseUrl`, `console.profile.field.apiKeyHeader`, `console.profile.field.apiKeyValue`, `console.profile.field.override.auth`, `console.profile.field.override.vehicle`, `console.profile.field.override.alerts`, `console.profile.keyStored`, `toggle_clear_stored_key`, `toggle_trust_user_certificates`, `console.profile.error`, `console.profile.save` |
| Delete profile | Only for user profiles. A dialog asks first. A built-in profile cannot be deleted. When the user deletes the active profile, the app falls back to the cloud profile. | `console.profile.delete`, `console.profile.delete.confirm`, `console.profile.delete.yes`, `console.profile.delete.no` |
| Export | "Copy" puts the profile list on the clipboard as JSON. "Share" opens the share sheet. Both leave out API key values. The result card shows the count. | `console.export.copy`, `console.export.share` |
| Import | A dialog with a text field. "Paste from clipboard" fills it. One invalid profile rejects the whole text. An import keeps the API key already stored for the same profile id. | `console.import`, `console.import.dialog`, `console.import.text`, `console.import.paste`, `console.import.confirm`, `console.import.cancel` |
| VIN override | A text field, "Set VIN" and "Clear VIN". The console calls use this VIN. The app replaces it with a vehicle of the account when it loads the vehicle list. | `console.vin`, `console.vin.set`, `console.vin.clear` |
| Reset session | Signs out, sets the scenario to `default`, and keeps profiles, the VIN and (by default) the PIN. The checkbox "Also clear the PIN" clears the PIN. The app then opens Login. | `console.reset_session`, `console.reset_clear_pin` |

## Design tokens

The values are in [Color.kt](../core/designsystem/src/main/kotlin/com/drivelink/core/designsystem/theme/Color.kt), [Type.kt](../core/designsystem/src/main/kotlin/com/drivelink/core/designsystem/theme/Type.kt) and [Theme.kt](../core/designsystem/src/main/kotlin/com/drivelink/core/designsystem/theme/Theme.kt). The light-theme colors come from the reference measurements in [PLAN.md](PLAN.md).

| Token | Value |
| --- | --- |
| Font | Manrope (SIL Open Font License, bundled). Large numbers use Light weight. |
| Corner radius | Cards 16 dp, tiles 14 dp, sheets 24 dp. Buttons and chips are full pills. |
| Spacing | Screen margin 20 dp; scale 4, 8, 12, 16, 24, 32 dp |
| Dark theme | Original design (the reference has no dark theme). Navy-black background, blue accent. |

## Accessibility

- Every tile has a content description (the label) and a state description ("On", "Off", "In progress").
- Stats and stat-grid cells merge into one spoken item, for example "72 %, Battery".
- The battery bar reads "Battery 72 percent, limit 80 percent".
- Most touch targets are 48 dp or larger. The Home status chip is about 32 dp high. A clickable chip has a 48 dp touch area (done in Phase 5).

## Known differences from the reference

| Item | Reference | DriveLink | Reason |
| --- | --- | --- | --- |
| Vehicle image | 3/4 photo render | Flat side-profile illustration | No OEM images; the original art can change color per vehicle |
| Font | OEM font | Manrope | No OEM fonts |
| Logo | OEM mark | "D" circle and "DriveLink" wordmark | No OEM marks |
| Charging view | Bottom sheet with a close button | Full screen with a back button | Simpler navigation for tests |
| Maps | Live map with traffic | osmdroid with OpenStreetMap tiles | No map API key needed |

## Phase 5 test tags (stage 1)

| Area | Tags |
| --- | --- |
| Login | `screen_login`, `login_email`, `login_password`, `login_submit`, `login_loading`, `login_notice`, `login_error`, `login_correlation_id` |
| PIN setup and PIN prompt | `screen_pin_setup`, `pin_setup_hint`, `screen_pin`, `pin_dots`, `pin_key_0` to `pin_key_9`, `pin_key_delete`, `pin_error` |
| Home | `screen_home`, `home_refresh`, `home_loading`, `status_chip`, `row_location`, `row_status`, `row_health`, `row_trips`, `topbar_alerts` |
| Vehicle picker | `vehicle_switcher`, `vehicle_picker`, `vehicle_option_<vin>` |
| Error and retry | `error_state`, `error_message`, `error_correlation_id`, `error_retry`, `banner_action`, `banner_stale`, `banner_warning`, `banner_error`, `banner_info` |
| Command card | `command_progress`, `command_stage`, `command_retry`, `command_dismiss` |
| Remote Start | `preset_0` to `preset_3`, `climate_temperature`, `climate_heated_seats`, `climate_heated_seats_0` to `climate_heated_seats_3`, `climate_duration`, `climate_start` |
| Menu | `menu_demo_console`, `menu_sign_out` |

The activity root, the vehicle picker sheet and the Demo console set `testTagsAsResourceId`, so UiAutomator and Perfecto see the tags as resource IDs.

## Phase 5 stage 1 screenshots

Captured on the API 37 emulator (1080 x 2400) against the local mock. Compare them with the Phase 1 screenshots 01 to 09: the layout is the same.

| Screenshot | Shows |
| --- | --- |
| [p5-01](ui/screens/p5-01-login.png) | Login with the demo credentials |
| [p5-02](ui/screens/p5-02-pin-setup.png) | PIN setup, confirm step, two digits typed |
| [p5-03](ui/screens/p5-03-home.png) | Home, EV, default data |
| [p5-04](ui/screens/p5-04-home-dark.png) | Home, dark theme |
| [p5-05](ui/screens/p5-05-home-ice.png) | Home, gas car (Solace) |
| [p5-06](ui/screens/p5-06-pin.png) | PIN prompt before a command |
| [p5-07](ui/screens/p5-07-command-waiting.png) | Command card, "Waiting for vehicle…" |
| [p5-08](ui/screens/p5-08-command-done.png) | Command card, "Done" |
| [p5-09](ui/screens/p5-09-command-failed.png) | Command card, failed with the reason and Retry |
| [p5-10](ui/screens/p5-10-controls.png) | Remote Controls |
| [p5-11](ui/screens/p5-11-climate.png) | Remote Start, Winter preset, heated seat level 3 |
| [p5-12](ui/screens/p5-12-home-offline.png) | Home, `vehicle-offline` |
| [p5-13](ui/screens/p5-13-home-low-battery.png) | Home, `low-battery` |
| [p5-14](ui/screens/p5-14-home-error.png) | Home, `server-error` |

## Phase 5 stage 2 screenshots

Captured on the API 37 emulator (1080 x 2400) against the local mock, light theme, scenario `default` unless the table says otherwise. The EV is the Aurora, unless it says gas car.

| Screenshot | Shows |
| --- | --- |
| [p5-15](ui/screens/p5-15-status-quick.png) | Vehicle Status, Quick View |
| [p5-16](ui/screens/p5-16-status-full.png) | Vehicle Status, Full List |
| [p5-17](ui/screens/p5-17-charging.png) | Charging |
| [p5-18](ui/screens/p5-18-charge-schedule.png) | Charging Schedule |
| [p5-19](ui/screens/p5-19-carcare.png) | Car Care |
| [p5-20](ui/screens/p5-20-service-request.png) | Schedule Service, filled form |
| [p5-20b](ui/screens/p5-20b-service-confirmation.png) | Schedule Service, confirmation |
| [p5-21](ui/screens/p5-21-trips.png) | Trips |
| [p5-22](ui/screens/p5-22-alerts.png) | Alerts |
| [p5-23](ui/screens/p5-23-maps.png) | Maps with tiles, marker and the vehicle card |
| [p5-24](ui/screens/p5-24-menu.png) | Menu |
| [p5-25](ui/screens/p5-25-settings.png) | Settings |
| [p5-26](ui/screens/p5-26-console-tools.png) | Demo console, setup tools open |
| [p5-27](ui/screens/p5-27-home-after-unlock.png) | Home, gas car, after Unlock: the Lock tile and the chip show "Unlocked" |
| [p5-28](ui/screens/p5-28-status-dark.png) | Vehicle Status, dark theme |
| [p5-29](ui/screens/p5-29-maps-dark.png) | Maps, dark theme (inverted tiles) |
| [p5-30](ui/screens/p5-30-carcare-dark.png) | Car Care, dark theme |
| [p5-31](ui/screens/p5-31-status-offline.png) | Vehicle Status, `vehicle-offline` (stale banner) |
| [p5-32](ui/screens/p5-32-home-offline-command.png) | Home, `vehicle-offline`, the command card shows "The vehicle is offline. Try again when it has a connection." |
| [p5-33](ui/screens/p5-33-trips-rate-limited.png) | Trips, `rate-limited` (error card with the wait and the correlation id) |

## Demo console test tags (Phase 4)

The debug console opens with the launch extra `screen=console` or from the Menu row "Demo Console" (`menu_demo_console`). The root sets `testTagsAsResourceId`, so UiAutomator and Perfecto see the tags as resource IDs.

| Area | Tags |
| --- | --- |
| Console | `screen_console`, `console.profile`, `console.profile.<id>`, `console.scenario`, `console.scenario.<name>`, `console.target`, `console.localNetworkWarning` |
| Actions | `console.signIn`, `console.health`, `console.vehicles`, `console.status`, `console.lock`, `console.commandProgress` |
| Result | `console.result`, `console.result.message`, `console.result.correlationId` |
| Inspector list | `inspector.list`, `inspector.row.<n>` (0 is the newest), `inspector.empty`, `inspector.clear` |
| Inspector detail | `inspector.detail`, `inspector.detail.copyCurl`, `inspector.detail.status`, `inspector.detail.url`, `inspector.detail.requestBody`, `inspector.detail.responseBody` |

## Review checklist for Ben

- [ ] The Home screen reads as the reference pattern at a glance.
- [ ] The colors, font and density are close enough.
- [ ] The vehicle art is acceptable, or you want a different style (for example, a 3/4 view).
- [ ] The test tags are acceptable for the Perfecto scripted and AI tests.
- [ ] Anything to add before Phase 2 (for example, Digital Key or Surround View screens).
