# centripetal-indicators-service

Little web service that allows clients to do simple queries on an open source 
intelligence feed of JSON documents from by AlienVault OTX. The data is
contained in the file found at `resources/indicators.json`.

## HTTP API

| method | url             | description                                      |
|--------|-----------------|--------------------------------------------------|
| GET    | /indicators     | With no query parameters all documents are returned. To limit which documents are returned query parameters can be provided. See "Query Parameters" section. |
| GET    | /indicators/:id | Returns a single document that is identified by the given id. |  
| POST   | /indicators     | Return documents that match the query provided in the body encoded in JSON. See "Query Language" section. | 

### Query Parameters
The `GET /indicators` endpoint accepts 0 or more query parameters. Parameters
take the form of `path=value`. The `path` is a path to a value in a JSON 
document in the data set. The `value` is the value to match. Parameters are 
combined in the usual way with a `&`. Documents that match all parameters are
returned. 

Example:

`?tlp=green&indicators.type=IPv4` 

This query matches documents that have a top level key of `"tlp"` with a value
of `"green"` and a at least one sub document in the array found at the
`"indicators"` key that has a `"type"` key with the value of `IPv4`.

### Query Language
A simple matching language encoded in JSON is provided for search capabilities.
When a query written in this language is sent as an HTTP request body to the 
`POST /indicators` endpoint the service will return all documents that match.

The language supports 4 operators. 
* `=` for matching a path / value combination, see the "Query Parameters"
  section.
* `not` negates an expression
* `and` takes one or more expression. Documents much match all given
  expressions to match.
* `or` takes one or more expressions. Documents much match at least one
  expression to match.

Examples:

```
["=" "tlp" "green"]
```

Negate the last example
```
["not" ["=" "tlp" "green"]]
```

Same as the query parameter example from above:
```
["and" 
 ["=" "tlp" "green"]
 ["=" "indicators.type" "IPv4"]]
```

Matches all documents that have a tag of `"elastichoney"` or a tag of 
`"conpot"`:
```
["or" 
 ["=" "tags" "elastichoney"]
 ["=" "tags" "conpot"]]
```

Expressions are composable:
```
["and"
 ["not" ["=" "tlp" "green"]]
 ["or" 
  ["=" "tags" "elastichoney"]
  ["=" "tags" "conpot"]]]
```

#### Grammar
```
query = bool-exp
bool-exp = equals | or | and | not
equals = "[" "=" path value "]"
value = "true" | "false" | number | string
path = json-key | json-key "." path 
json-key = string
or = "[" "or" bool-exp+ "]" 
and = "[" "and" bool-exp+ "]" 
not = "[" "not" bool-exp "]"
```

## Running tests
```
$ lein test
```

## Running the service 

REPL:
```
$ lein repl
```

Run Locally with Leiningen
```
$ lein run
```

Build Docker Image
```
$ ./docker-build.sh
```

Run Locally with Docker
```
$ ./docker-build.sh
$ ./docker-run.sh
```

## Inspiration
* PostgreSQL's generalized inverted indexes (GIN)
* [Pedestal Documentation](https://pedestal.io/pedestal/0.7/index.html)
* [Clojure Spec](https://clojure.org/guides/spec) for query language validation and specification

## License

Copyright © 2025 Jason Kapp 

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
