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

#### TRACON Boundaries

Approach and departure controllers are painted with their boundary from the [SimAware TRACON Project](https://github.com/vatsimnetwork/simaware-tracon-project). Controllers without a known boundary keep the approach circle.

#### Vatglasses

Sectors from [VATGlasses](https://github.com/lennycolton/vatglasses-data) are painted for the online controllers at a single flight level, which is shown in the status bar. The flight level is chosen as follows:

| Priority | Flight level of                                                               |
|:---------|:------------------------------------------------------------------------------|
| 1        | The flight whose VG button was pressed in its details                         |
| 2        | Your own flight, if a CID is set and automatic mode is enabled                |
| 3        | The manual flight level                                                       |

CID, automatic mode and manual flight level are set in the Vatglasses settings. Runway dependent sectors are painted for the first runway configuration listed for the airport.

Both data sets are downloaded from GitHub into the cache directory and checked for updates once a day.

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