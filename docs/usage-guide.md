# Usage Guide

### [← back](/)

## Navigation

#### General

| Input             | Action            |
|:-------------     |:------------------|
| CTRL + F          | Focus Search Box  |

#### Search

| Input             | Action            |
|:-------------     |:------------------|
| ESC               | Clear selection   |

#### Map

| Input             | Action            |
|:-------------     |:------------------|
| LMB               | Pan the map       |
| RMB               | Open Context Menu |
| MMB               | Select Item       |
| CTRL + LMB        | Distance Measure  |
| ESC               | Clear Selection   |
| +, E              | Zoom In           |
| -, Q              | Zoom Out          |
| W, A, S, D        | Pan Map           |

#### Tables

| Input             | Action            |
|:-------------     |:------------------|
| LMB               | Select Item       |
| CTRL+LMB          | Open Stream (Streamer Table) |

## Airspace

TRACON boundaries and Vatglasses sectors are two ways of showing the same airspace, so enabling one disables the other. TRACON boundaries are the default. Both can be selected like FIRs, through the context menu or the middle mouse button.

#### TRACON Boundaries

Approach and departure controllers are painted with their boundary from the [SimAware TRACON Project](https://github.com/vatsimnetwork/simaware-tracon-project). Controllers without a known boundary keep the approach circle.

#### Vatglasses

Sectors from [VATGlasses](https://github.com/lennycolton/vatglasses-data) are painted for the online controllers at a single flight level, which is shown in the status bar. The flight level is chosen as follows:

| Priority | Flight level of                                                               |
|:---------|:------------------------------------------------------------------------------|
| 1        | The flight whose VG button was pressed in its details                         |
| 2        | Your own flight, if a CID is set and automatic mode is enabled                |
| 3        | The manual flight level                                                       |

CID, automatic mode and manual flight level are set in the Vatglasses settings. With "Automatically switch to VG when my CID is online" enabled, Vatglasses is switched on when you connect and off when you disconnect; toggling it yourself in between is kept until your connection changes. While Vatglasses is on, a controller it has sectors for is painted with those sectors only, any other controller keeps its FIR, UIR or approach circle. Runway dependent sectors are painted for the first runway configuration listed for the airport.

Both data sets are downloaded from GitHub into the cache directory and checked for updates once a day.

## Events

The pennon button in the toolbar shows events from the [VATSIM Events API](https://vatsim.dev/api/events-api/list-all-events). While it is enabled, every airport with a running or upcoming event is marked with a pennon, and its details list those events above the controllers. The name of an event opens the event details.

Which events are shown is set in the Events settings:

| Setting                  | Default                            |
|:-------------------------|:-----------------------------------|
| Types                    | Events and controller examinations |
| Maximum hours ahead      | 48                                 |
| Reminder minutes before  | 30                                 |

The color of the pennon is part of the color scheme and can be changed in the Events painter settings.

#### Event Details

| Button   | Action                                                                                   |
|:---------|:-----------------------------------------------------------------------------------------|
| Calendar | Opens the event in the default calendar application of your operating system             |
| Reminder | Sends a notification before the event starts, press again to remove the reminder         |

Reminders are saved and survive a restart, but are only sent while VATprism is running. Your operating system may ask you to allow notifications when the first reminder is set.

Events are loaded when they are first shown and at most once per hour after that.

## Bookings

The calendar button in the toolbar shows the positions controllers have booked at [VATSIM ATC Bookings](https://atc-bookings.vatsim.net). While it is enabled, every position with a running or upcoming booking is painted as if it was online, but in grey: airport positions, the approach circle, FIRs, UIRs, TRACON boundaries and Vatglasses sectors. A position which is already online keeps its regular look.

The details of an airport, FIR, UIR, TRACON boundary or Vatglasses position list all of its bookings above the controllers, including those of positions which are online. The section is hidden while there are no bookings.

| Setting             | Default | Maximum      |
|:--------------------|:--------|:-------------|
| Maximum hours ahead | 3       | 168 (7 days) |

The grey of the booked positions can be changed in the Booked Firs, Booked Uirs, Booked Tracon, Booked Vatglasses and Airports painter settings.

Bookings do not include a frequency, so a booked position is only matched to a Vatglasses position if no other position shares its callsign prefix and type. Such a booking still activates its FIR or airport.

Bookings are loaded when they are first shown and at most every 15 minutes after that.

## Configuration Directories

##### Root Configuration Directory

    %LOCALAPPDATA%\VATprism

##### General Configuration

    %LOCALAPPDATA%\VATprism\Config.json

##### Color Schemes

    %LOCALAPPDATA%\VATprism\ColorSchemes

##### Filters

    %LOCALAPPDATA%\VATprism\Filters

##### Logs

    %LOCALAPPDATA%\VATprism\Logs

##### Data Files

If you want to test changes to the data files locally, you can place the files in the following locations. Either or
both work and will replace the version fetched when VATprism is starting up.

    %LOCALAPPDATA%\VATprism\VATSpy.dat

<!-- -->

    %LOCALAPPDATA%\VATprism\FIRBoundaries.dat

### [← back](/)