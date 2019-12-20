#include "database.h"

#include <realm/group_shared.hpp>
#include <results.hpp>

#include <iostream>
#include <json.hpp>
#include <stdexcept>

using json = nlohmann::json;

class SchemaFormatError : public std::runtime_error {
public:
    SchemaFormatError(const std::string& msg)
	: std::runtime_error(msg) {}
};

static inline void parse_property_type(StringData object_name, Property& prop, StringData type)
{
    using realm::PropertyType;
    if (!type || !type.size()) {
        throw std::logic_error(util::format("Property '%1.%2' must have a non-empty type", object_name, prop.name));
    }
    if (type.ends_with("[]")) {
        prop.type |= PropertyType::Array;
        type = type.substr(0, type.size() - 2);
    }
    if (type.ends_with("?")) {
        prop.type |= PropertyType::Nullable;
        type = type.substr(0, type.size() - 1);
    }

    if (type == "bool") {
        prop.type |= PropertyType::Bool;
    }
    else if (type == "int") {
        prop.type |= PropertyType::Int;
    }
    else if (type == "float") {
        prop.type |= PropertyType::Float;
    }
    else if (type == "double") {
        prop.type |= PropertyType::Double;
    }
    else if (type == "string") {
        prop.type |= PropertyType::String;
    }
    else if (type == "date") {
        prop.type |= PropertyType::Date;
    }
    else if (type == "data") {
        prop.type |= PropertyType::Data;
    }
    else if (type == "list") {
        if (prop.object_type == "bool") {
            prop.type |= PropertyType::Bool | PropertyType::Array;
            prop.object_type = "";
        }
        else if (prop.object_type == "int") {
            prop.type |= PropertyType::Int | PropertyType::Array;
            prop.object_type = "";
        }
        else if (prop.object_type == "float") {
            prop.type |= PropertyType::Float | PropertyType::Array;
            prop.object_type = "";
        }
        else if (prop.object_type == "double") {
            prop.type |= PropertyType::Double | PropertyType::Array;
            prop.object_type = "";
        }
        else if (prop.object_type == "string") {
            prop.type |= PropertyType::String | PropertyType::Array;
            prop.object_type = "";
        }
        else if (prop.object_type == "date") {
            prop.type |= PropertyType::Date | PropertyType::Array;
            prop.object_type = "";
        }
        else if (prop.object_type == "data") {
            prop.type |= PropertyType::Data | PropertyType::Array;
            prop.object_type = "";
        }
        else {
            if (is_nullable(prop.type)) {
                throw std::logic_error(util::format("List property '%1.%2' cannot be optional", object_name, prop.name));
            }
            if (is_array(prop.type)) {
                throw std::logic_error(util::format("List property '%1.%2' must have a non-list value type", object_name, prop.name));
            }
            prop.type |= PropertyType::Object | PropertyType::Array;
        }
    }
    else if (type == "linkingObjects") {
        prop.type |= PropertyType::LinkingObjects | PropertyType::Array;
    }
    else if (type == "object") {
        prop.type |= PropertyType::Object;
    }
    else {
        // The type could be the name of another object type in the same schema.
        prop.type |= PropertyType::Object;
        prop.object_type = type;
    }

    // Object properties are implicitly optional
    if (prop.type == PropertyType::Object && !is_array(prop.type)) {
        prop.type |= PropertyType::Nullable;
    }
}

realm::ObjectSchema parse_object_schema(json &object_schema)
{
	realm::ObjectSchema os;
	os.name = object_schema["name"];

	auto pk = object_schema.find("primaryKey");
	if (pk != object_schema.end()) {
		os.primary_key = *pk;
	}

	for (auto &json_prop : object_schema["properties"].items())
	{
		std::string prop_name = json_prop.key();
		json::value_t value_type = json_prop.value().type();
		Property prop;
		prop.name = prop_name;
		if (value_type == json::value_t::string) {
			std::string value = json_prop.value();
			parse_property_type(os.name, prop, value);
		} else if (value_type == json::value_t::object) {
			json prop_object = json_prop.value();

			auto prop_type = prop_object.find("type");
			if (prop_type == prop_object.end()) {
				throw SchemaFormatError(util::format("Schema for '%1.%2' must specify a 'type'", os.name, prop.name));
			}
			auto object_type = prop_object.find("objectType");
			if (object_type != prop_object.end()) {
				prop.object_type = *object_type;
			}
			std::string value = prop_type.value();
			parse_property_type(os.name, prop, value);

			auto indexed = prop_object.find("indexed");
			if (indexed != prop_object.end()) {
				std::string is_indexed = *indexed;
				if (is_indexed == "true") {
					prop.is_indexed = true;
				} else if (is_indexed != "false") {
					throw SchemaFormatError(util::format("Schema for '%1.%2' must specify either \"true\" or \"false\" for attribute 'indexed'", os.name, prop.name));
				}
			}

			auto optional = prop_object.find("optional");
			if (optional != prop_object.end()) {
				std::string is_optional = *optional;
				if (is_optional == "true") {
					prop.type |= PropertyType::Nullable;
				} else if (is_optional != "false") {
					throw SchemaFormatError(util::format("Schema for '%1.%2' must specify either \"true\" or \"false\" for attribute 'optional'", os.name, prop.name));
				}
			}

			if (prop.type == PropertyType::Object && prop.object_type.empty()) {
				auto object_type = prop_object.find("objectType");
				if (object_type == prop_object.end()) {
					throw SchemaFormatError(util::format("%1 property %2.%3 must specify 'objectType'",
														is_array(prop.type) ? "List" : "Object", os.name, prop.name));
				}
				prop.object_type = *object_type;
			}
			if (prop.type == PropertyType::LinkingObjects) {
				auto object_type = prop_object.find("objectType");
				if (object_type == prop_object.end()) {
					throw SchemaFormatError(util::format("Linking objects property %1.%2 must specify 'objectType'",
														os.name, prop.name));
				}
				prop.object_type = *object_type;
				auto link_prop = prop_object.find("property");
				if (link_prop == prop_object.end()) {
					throw SchemaFormatError(util::format("Linking objects property %1.%2 must specify 'property'",
														os.name, prop.name));
				}
				prop.link_origin_property_name = *link_prop;
			}
		} else {
			throw SchemaFormatError(util::format("Schema for '%1.%2' must be a string or an object", os.name, prop.name));
		}

		if (!prop.name.empty() && prop.name == os.primary_key) {
			prop.is_primary = true;
		}
		if (prop.link_origin_property_name.empty()) {
			os.persisted_properties.push_back(prop);
		} else {
			os.computed_properties.push_back(prop);
		}
	}
	return os;
}

realm::Schema parse_schema(const char* schema)
{
	json json = json::parse(schema);
	std::vector<ObjectSchema> schemas;
	for (auto it : json) {
		schemas.push_back(parse_object_schema(it));
	}
	return Schema{schemas};
}

Database::Database(const char *name, const char* schema)
{
	Realm::Config config;
	config.schema_version = 1;
	config.schema = parse_schema(schema);
	config.path = name;
	this->m_realm = Realm::get_shared_realm(config);
}

Database::~Database()
{
	if (this->m_realm) {
		this->m_realm->close();
	}
}

KeyPathMapping Database::getKeyPathMappings()
{
	if (!m_mappings) {
		m_mappings = KeyPathMapping();
		realm::alias_backlinks(*m_mappings, *m_realm);
	}
	return *m_mappings;
}
