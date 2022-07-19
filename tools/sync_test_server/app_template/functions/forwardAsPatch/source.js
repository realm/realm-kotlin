exports = async function (url, body) {
    const response = await context.http.patch({
        url: url,
        body: body,
        headers: context.request.requestHeaders,
        encodeBodyAsJSON: true
    });

    return response;
}; 