# Telemetry Dashboard Generator

The clojurescript/om based [dashboard](http://vitillo.github.io/iacomus/resources/public/index.html?config=sample_config.json) can display and compare data collected with [Telemetry](https://wiki.mozilla.org/Telemetry) on a weekly basis. It picks up
a configuration file, specified through a GET parameter, that contains a description of the data format, e.g:

```javascript
{
  "sort-options": {
    "values": ["Impact", "Popularity", "Median (ms)", "75% (ms)"],
    "selected": "Impact"
  },
  "filter-options": [
    {"id": "Application",
     "values": ["Firefox", "Fennec"],
     "selected": "Firefox"},
    {"id": "Platform",
     "values": ["WINNT", "Linux", "Darwin", "Android"],
     "selected": "WINNT"},
    {"id": "Measure",
     "values": ["startup_MS", "shutdown_MS"],
     "selected": "startup_MS"},
    {"id": "Limit",
     "values": [10, 25, 100],
     "selected": 10}
  ],
  "title": ["Telemetry Add-on Performance", "Bootstrap add-on start up and shut down times"],
  "main-header": "Name",
  "header": ["Application", "Platform", "Addon ID", "Version", "Name", "Measure",
             "Sessions", "Popularity", "Impact", "Median (ms)", "75% (ms)", "95% (ms)"],
  "url-prefix": "https://s3-us-west-2.amazonaws.com/telemetry-public-analysis/addon_perf/data/weekly_addons"
}
```

Where
- `sort-options` specifies the fields the dashboard should allow sorting on;
- `filter-options` is a list of filter descriptors which specifiy the filterable columns and the allowed values to filter on;
- `main-header` is the name of the main column, i.e. the primary key used to identify uniquely a row;
- `url-prefix` is the url prefix that the dashboard uses to concatenate the date of the requested dataset.

The filename for a dataset of a given week should follow the pattern: `url-prefix20140804.csv.gz`, where `20140804` is the date of Monday.
