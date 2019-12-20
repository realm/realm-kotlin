#include <iostream>

#include "wrapper.h"
#include "database.h"

#include <impl/object_accessor_impl.hpp>
#include <list.hpp>
#include <object_store.hpp>

#include <realm/group_shared.hpp>

#include <realm/parser/parser.hpp>
#include <realm/parser/query_builder.hpp>


#define typeof __typeof__

struct database
{
	void *db;
};

struct realm_object
{
	void *obj;
};

struct realm_results
{
	void *results;
};

struct realm_list
{
	void *list;
};

database_t *create(const char *db_name, const char *schema)
{
	database_t *db_ptr;
	Database *db;

	db_ptr = new database_t();
	db = new Database(db_name, schema);
	db_ptr->db = db;

	return db_ptr;
}

void destroy(database_t *db_ptr)
{
	if (db_ptr == nullptr)
		return;
	delete static_cast<Database *>(db_ptr->db);
	delete static_cast<database_t *>(db_ptr);
}

// ***********      REALM      *********** //

void begin_transaction(database_t *db_ptr)
{
	Database *db = static_cast<Database *>(db_ptr->db);
	db->realm()->begin_transaction();
}

void commit_transaction(database_t *db_ptr)
{
	Database *db = static_cast<Database *>(db_ptr->db);
	db->realm()->commit_transaction();
}

void cancel_transaction(database_t *db_ptr)
{
	Database *db = static_cast<Database *>(db_ptr->db);
	db->realm()->cancel_transaction();
}

// *********** OBJECT ACCESSOR *********** //

realm_object_t *add_object(database_t *db_ptr, const char *object_type)
{
	Database *db = static_cast<Database *>(db_ptr->db);
	std::string table_name = realm::ObjectStore::table_name_for_object_type(object_type);
	auto &table = *db->realm()->read_group().get_table(table_name);
	size_t row_ndx = table.add_empty_row();
	Object *obj = new Object(db->realm(), object_type, row_ndx);

	realm_object_t *obj_ptr;
	obj_ptr = (typeof(obj_ptr))malloc(sizeof(*obj_ptr)); //TODO heap allocation, remember to deallocate either manually or using finalizers once Dart supports it
	obj_ptr->obj = obj;

	return obj_ptr;
}

void delete_object(database_t *db_ptr, realm_object_t* instance)
{
	realm::Object *obj = static_cast<realm::Object *>(instance->obj);
	obj->row().move_last_over();
}

int8_t object_get_bool(realm_object_t *obj_ptr, const char *property_name)
{
	realm::Object *obj = static_cast<realm::Object *>(obj_ptr->obj);
	realm::CppContext context(obj->realm()); //FIXME is this the best way to get the context (caching?)
	return any_cast<bool>(obj->get_property_value<util::Any>(context, property_name)) == true ? 1 : 0;
}

int64_t object_get_int64(realm_object_t *obj_ptr, const char *property_name)
{
	realm::Object *obj = static_cast<realm::Object *>(obj_ptr->obj);
	realm::CppContext context(obj->realm());
	return any_cast<int64_t>(obj->get_property_value<util::Any>(context, property_name));
}

double object_get_double(realm_object_t *obj_ptr, const char *property_name)
{
	realm::Object *obj = static_cast<realm::Object *>(obj_ptr->obj);
	realm::CppContext context(obj->realm());
	return any_cast<double>(obj->get_property_value<util::Any>(context, property_name));
}

const char *object_get_string(realm_object_t *obj_ptr, const char *property_name)
{
	realm::Object *obj = static_cast<realm::Object *>(obj_ptr->obj);
	realm::CppContext context(obj->realm());
	std::string value = realm::util::any_cast<std::string>(obj->get_property_value<util::Any>(context, property_name));
	char *data = new char[value.size()];
	std::strcpy(data, value.c_str());
	return data;
}

realm_object_t* object_get_object(realm_object_t *obj_ptr, const char* property_name)
{
	realm::Object *obj = static_cast<realm::Object *>(obj_ptr->obj);
	realm::CppContext context(obj->realm());

	util::Any property_value = obj->get_property_value<util::Any>(context, property_name);
	if (property_value.has_value()) {
		realm::Object* objectLink = new realm::Object(realm::util::any_cast<realm::Object>(property_value));

		realm_object_t *object_ptr = new realm_object_t();
		object_ptr->obj = objectLink;
		return object_ptr;
	} else {
		return nullptr;
	}
}

realm_list_t* object_get_list(realm_object_t *obj_ptr, const char* property_name)
{
	realm::Object *obj = static_cast<realm::Object *>(obj_ptr->obj);
	realm::CppContext context(obj->realm());
	realm::List *value = new List(realm::util::any_cast<realm::List>(obj->get_property_value<util::Any>(context, property_name)));
	realm_list_t *list_ptr = new realm_list_t();
	list_ptr->list = value;
	return list_ptr;
}

realm_results_t* object_get_linkingobjects(realm_object_t *obj_ptr, const char *property_name)
{
	realm::Object *obj = static_cast<realm::Object *>(obj_ptr->obj);
	realm::CppContext context(obj->realm());
	realm::Results *value = new Results(realm::util::any_cast<realm::Results>(obj->get_property_value<util::Any>(context, property_name)));
	realm_results_t *results_ptr = new realm_results_t();
	results_ptr->results = value;
	return results_ptr;
}

template <typename ValueType>
void object_set_value(realm_object_t *obj_ptr, const char *property_name, ValueType value)
{
	realm::Object *obj = static_cast<realm::Object *>(obj_ptr->obj);
	realm::CppContext context(obj->realm()); //FIXME is this the right way to invoke the set_property? can we cache the context?
	obj->set_property_value(context, property_name, value, false);
}

void object_set_bool(realm_object_t *obj_ptr, const char *property_name, int8_t value)
{
	object_set_value(obj_ptr, property_name, util::Any(value == true ? 1 : 0));
}

void object_set_int64(realm_object_t *obj_ptr, const char *property_name, int64_t value)
{
	object_set_value(obj_ptr, property_name, util::Any(value));
}

void object_set_double(realm_object_t *obj_ptr, const char *property_name, double value)
{
	object_set_value(obj_ptr, property_name, util::Any(value));
}

void object_set_string(realm_object_t *obj_ptr, const char *property_name, const char *value)
{
	object_set_value(obj_ptr, property_name, util::Any(std::string(value)));
}

void object_set_object(realm_object_t *obj_ptr, const char* property_name, realm_object_t *value)
{
	object_set_value(obj_ptr, property_name, util::Any(*static_cast<realm::Object *>(value->obj)));//TODO should we call is_valid(); for any Object passed?
}

realm_results_t* query(database_t *db_ptr, const char *object_type, const char* query_string)
{
	Database *db = static_cast<Database *>(db_ptr->db);
	auto &table = *db->realm()->read_group().get_table(std::string("class_").append(object_type));
	Query query = table.where();

	realm::query_builder::NoArguments args;
	parser::ParserResult res = realm::parser::parse(query_string);
	realm::query_builder::apply_predicate(query, res.predicate, args, db->getKeyPathMappings());

	Results* results = new Results(db->realm(), query);

	realm_results_t *results_ptr;
	results_ptr = (typeof(results_ptr))malloc(sizeof(*results_ptr));
	results_ptr->results = results;
	return results_ptr;
}

size_t realmresults_size(realm_results_t *realm_results_ptr)
{
	Results *results = static_cast<Results *>(realm_results_ptr->results);
	return results->size();
}

void realmresults_delete(realm_results_t *realm_results_ptr)
{
	Results *results = static_cast<Results *>(realm_results_ptr->results);
	results->clear();
}

realm_object_t* realmresults_get(realm_results_t *realm_results_ptr, const char* object_type, size_t row_ndx)
{
	Results *results = static_cast<Results *>(realm_results_ptr->results);
	auto row =  results->get(row_ndx);
	Object *obj = new Object(results->get_realm(), *results->get_realm()->schema().find(object_type), row);

	realm_object_t *obj_ptr;
	obj_ptr = (typeof(obj_ptr))malloc(sizeof(*obj_ptr));
	obj_ptr->obj = obj;

	return obj_ptr;
}

size_t realmlist_size(realm_list_t *realm_list_ptr)
{
	List *list = static_cast<List *>(realm_list_ptr->list);
	return list->size();
}

void realmlist_clear(realm_list_t *realm_list_ptr)
{
	List *list = static_cast<List *>(realm_list_ptr->list);
	list->remove_all();
}

void realmlist_insert(realm_list_t *realm_list_ptr, realm_object_t *obj_ptr, size_t index)
{
	List *list = static_cast<List *>(realm_list_ptr->list);
	realm::Object *obj = static_cast<realm::Object *>(obj_ptr->obj);
	list->insert(index, obj->row());
}

void realmlist_erase(realm_list_t *realm_list_ptr, size_t index)
{
	List *list = static_cast<List *>(realm_list_ptr->list);
	list->remove(index);
}

realm_object_t* realmlist_get(realm_list_t *realm_list_ptr, const char* object_type, size_t index)
{
	List *list = static_cast<List *>(realm_list_ptr->list);
	auto row =  list->get(index);
	Object *obj = new Object(list->get_realm(), *list->get_realm()->schema().find(object_type), row);

	realm_object_t *obj_ptr;
	obj_ptr = (typeof(obj_ptr))malloc(sizeof(*obj_ptr));
	obj_ptr->obj = obj;

	return obj_ptr;
}

void realmlist_set(realm_list_t *realm_list_ptr, realm_object_t *obj_ptr, size_t index)
{
	List *list = static_cast<List *>(realm_list_ptr->list);
	realm::Object *obj = static_cast<realm::Object *>(obj_ptr->obj);
	list->set(index, obj->row());
}
