## Why

The form stores one translation a line, but the dictionary's translations were joined on `, `: `кошка` and `кот` from the dictionary were stored as the single translation `кошка, кот` (#711). The join came from a time when the transport split a translation on commas; it now carries them whole.

## Changes

- The dictionary's translations fill the field one a line, so each is stored as its own translation.

## Capabilities

### New Capabilities
<!-- None. -->

### Modified Capabilities
- `home-add-form-focus`: prefilled translations come one a line.

## Decisions

- Owner chose one a line over keeping the comma join.
