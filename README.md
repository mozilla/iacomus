# Telemetry Dashboard Generator

This clojurescript/om based dashboard can display and compare data collected with [Telemetry](https://wiki.mozilla.org/Telemetry) on a weekly basis. It picks up
a configuration file, specified through a GET parameter, that contains a description of the data format, e.g:

```javascript
{
  "sort-options": {
      "values": ["Estimate (ms)", "Add-on", "Frequency (%)"],
      "selected": "Estimate (ms)"
    },
  "filter-options": [
      {"id": "Limit",
       "values": [25, 50, 100, 200, 500],
       "selected": 25
      }
    ],
  "title": ["Add-ons startup correlations", "Correlations between startup time and add-ons"],
  "description": ["A linear regression model is fit using the add-ons as predictors for the startup time. The job is run weekly on all the data collected on Monday for the release channel on Windows.",
                  "http://robertovitillo.com/2014/10/07/using-ml-to-correlate-add-ons-to-performance-bottlenecks/"],
  "primary-key": ["Add-on"],
  "header": ["Add-on", "Frequency (%)", "Estimate (ms)", "Error (ms)", "t-statistic"],
  "field-description": ["The name of the add-on", "The fraction of pings that contained the add-on", "The add-on coefficient expresses the effect of the addon on startup time wrt the average startup time without any add-ons", "The standard error of the coefficient", "The value of the associated t-statistic for the coefficient"],
  "url-prefix": "https://s3-us-west-2.amazonaws.com/telemetry-public-analysis/addon_analysis/data/startup_addon_summary"
}
```

Where
- `sort-options` specifies the fields the dashboard should allow sorting on;
- `filter-options` is a list of filter descriptors which specifiy the filterable columns and the allowed values to filter on;
- `primary-key` is the collection of fields that constitute the primary key which is used to identify uniquely a row;
- `description` is the overall description of the dashboard with an optional URL that links to the code or blogpost (optional);
- `header` is the list of column headers (optional);
- `field-description` is a list of descriptions of the column headers;
- `url-prefix` is the url prefix that the dashboard uses to concatenate the date of the requested dataset.

The filename for a dataset of a given week should follow the pattern: `url-prefix20140804.csv.gz`, where `20140804` is the date of the Monday of the requested week.
