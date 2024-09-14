# SF6 Frame Data Extractor

SF6 Webscraper extracts frame data from the official Street Fighter 6 website and saves it as JSON files.

## Prerequisites

- Clojure

- Leiningen

## Usage

Run the extractor from the command line, providing character names as arguments:

    $ lein run aki --format json

This will create JSON files aki_moves_data.json in the current directory.

    $ lein run guile --format csv

This will create JSON files guile_moves_data.csv in the current directory.

## Output Format

The tool generates JSON files with the following structure for each move:

```json
[
  {
    "move-type": "Normal",
    "name": "Move Name",
    "input": {
      "numerical-notation": "5LP",
      "text-notation": "LP"
    },
    "startup": "4",
    "active": "3",
    "recovery": "8",
    "on-hit": "+4",
    "on-block": "+2",
    "cancel-ability": "C",
    "damage": "300",
    "combo-scaling": ["Starter scaling 20%", " Combo scaling 15%"],
    "drive-gauge-gain-hit": "100",
    "drive-gauge-lose-dguard": "100",
    "drive-gauge-lose-punish": "100",
    "sa-gauge-gain": "100",
    "attribute": "Attribute",
    "notes": "Additional notes"
  },
  // ... more moves
]
```

## Dependencies

- [cheshire](https://github.com/dakrone/cheshire): JSON parsing
- [clj-http](https://github.com/dakrone/clj-http): HTTP client
- [enlive](https://github.com/cgrand/enlive): HTML parsing

## Todo

- [ ] Implement error handling for network issues
- [ ] Add support for batch processing of all characters

## Disclaimer

This tool is for educational purposes only. All data belongs to Capcom. Please use responsibly and respect Capcom's terms of service.
