exports = function (service, db, collection, document) {
    const mongodb = context.services.get(service);
    const result = mongodb
        .db(db)
        .collection(collection)
        .insertOne(document);

    return result;
};
