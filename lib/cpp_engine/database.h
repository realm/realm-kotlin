#ifndef __DATABASE_H__
#define __DATABASE_H__

#include <shared_realm.hpp>
#include <realm/util/optional.hpp>

#include <keypath_helpers.hpp>

using namespace realm;
using parser::KeyPathMapping;

class Database
{
public:
	Database(const char *name, const char* schema);
	~Database();
	SharedRealm const& realm() const { return m_realm; }
	KeyPathMapping getKeyPathMappings();
private:
	SharedRealm m_realm;
	util::Optional<KeyPathMapping> m_mappings;
};

#endif // __DATABASE_H__