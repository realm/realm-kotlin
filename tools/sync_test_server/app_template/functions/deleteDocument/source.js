exports = function (service, db, collection, query) {
    const mongodb = context.services.get(service);
    const result = mongodb
        .db(db)
        .collection(collection)
        .deleteMany(EJSON.parse(query));

    return result;
};
