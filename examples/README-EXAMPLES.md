# Unirest
The examples use [Unirest](http://unirest.io/java.html) to access HTTP services, many others are available.

# JSON
A textual markup, popular for web applications and services because of it's easy interoperability with JavaScript.

This is an overview of the structures in use for the exercise, for more details see e.g. [here](http://www.w3resource.com/JSON/introduction.php)

A JSON object
```
{
    "property-name" : "value"
}
```

A JSON object with a child-object
```
{
    "child-name" : {
        "property" : "value"
    }
}
```

A JSON object with more than one child (commas separate each object/property)
```
{
    "root" : {
        "child-1" : {
            "prop1": "foo",
            "prop2": "bar"
        },
        "child-2" : {
            "prop1": "foo",
            "prop2": "baz"
        }
    }
}
```

A JSON array
```
["foo", "bar", "baz"]
```