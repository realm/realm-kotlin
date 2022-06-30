#!/usr/bin/env nodejs

/**
 * This script controls the Command Server responsible for starting and stopping
 * ROS instances. The integration tests running on the device will communicate
 * with it using a predefined port in order to say when the ROS instance
 * should be started and stopped.
 *
 * This script is responsible for cleaning up any server state after it has been
 * stopped, so a new integration test will start from a clean slate.
 */

var winston = require('winston'); //logging
var http = require('http');
var url = require('url');
const fs = require('fs');
const { MongoClient, ObjectID } = require("mongodb");

const BAAS_HOST = process.argv[2];
winston.info(`BAAS host: ${BAAS_HOST}`)

const mdb_uri =
`mongodb://${BAAS_HOST}:26000/?readPreference=primary&directConnection=true&ssl=false`;
const parser = require('mongodb-query-parser');
const isPortAvailable = require('is-port-available');

function handleUnknownEndPoint(req, resp) {
    resp.writeHead(404, {'Content-Type': 'text/plain'});
    resp.end();
}

function handleOkHttp(req, resp) {
    var emitSuccess = req.url.endsWith("?success=true");
    if (emitSuccess) {
        resp.writeHead(200, {'Content-Type': 'text/plain'});
        resp.end(req.method + "-success");
    } else {
        resp.writeHead(500, {'Content-Type': 'text/plain'});
        resp.end(req.method + "-failure");
    }
}

function handleForwardPatchRequest(clientReq, clientResp) {
    let body = "";
    clientReq.on('data', chunk => {
        body += chunk.toString();
    });
    clientReq.on('end', () => {

        // Construct the intended request
        const forwardUrl = url.parse(clientReq.url, true).query["url"];

        // If pointing to a localhost, replace target by the actual local BAAS server
        // depending if the services are host inside or outside docker.

        // host.docker.internal -> external to docker
        // 127.0.0.1 -> internal to docker
        var urlParts = url.parse(forwardUrl.replace("127.0.0.1", BAAS_HOST), false);

        var options = {
            hostname: urlParts.hostname,
            port: urlParts.port,
            path: urlParts.path,
            method: 'PATCH',
            headers: clientReq.headers
        }

        // Forward the request to MongoDB Realm
        let forwardingRequest = http.request(options, (forwardingResponse) => {
            let forwardRespBody = ""
            forwardingResponse.on('data', chunk => {
                forwardRespBody +=  chunk.toString();
            })
            forwardingResponse.on('end', d => {
                clientResp.writeHead(forwardingResponse.statusCode, forwardingResponse.headers)
                clientResp.end(forwardRespBody);
            })
        });
        forwardingRequest.on('error', error => {
            clientResp.writeHead(500, {'Content-Type': 'application/json'});
            clientResp.end("Command server failed: " + error.toString());
        })
        forwardingRequest.write(body);
        forwardingRequest.end();
    });
}


function handleWatcher(req, resp) {
    resp.writeHead(200, {'Content-Type': 'text/event-stream'});

    resp.write("hello world 1\n");
    resp.write("hello world 2\n");
    resp.write("hello world 3\n");
}

function handleApplicationId(appName, req, resp) {
    switch(req.method) {
        case "GET":
            try {
                 const data = fs.readFileSync('/apps/' + appName + '/app_id', 'utf8')
                 console.log(data)
                 resp.writeHead(200, {'Content-Type': 'text/plain'});
                 resp.end(data.replace(/\n$/, ''));
            } catch (err) {
                 console.error(err)
                 resp.writeHead(404, {'Content-Type': 'text/plain'});
                 resp.end(err);
            }
            break;
        case "PUT":
            var body = [];
            req.on('data', (chunk) => {
                body.push(chunk);
            }).on('end', () => {
                body = Buffer.concat(body).toString();
                applicationIds[appName] = body.split("=")[1];
                resp.writeHead(201, {'Content-Location': '/application-id'});
                resp.end();
            });
            break;
        default:
            handleUnknownEndPoint(req, resp);
    }
}

async function handleMDBDocumentInsertRequest(clientReq, clientResp) {
  const client = new MongoClient(mdb_uri);
  try {
    var url_parts = url.parse(clientReq.url, true);

    const db_name = url_parts.query.db;
    const collection = url_parts.query.collection;

      switch(clientReq.method) {
          case "PUT":
              let data = '';
              clientReq.on('data', chunk => {
                data += chunk;
              })
              clientReq.on('end', async () => {
                  const payload = JSON.parse(data);

                  await client.connect();

                  const database = client.db(db_name);
                  const col = database.collection(collection);

                  clientResp.writeHead(200, {'Content-Type': 'application/json'});
                  var res = await col.insertOne(payload);
                  clientResp.end(JSON.stringify(res));
              });
      }

  } catch (err) {
     console.error(err)
     clientResp.writeHead(500, {'Content-Type': 'text/plain'});
     clientResp.end();
  } finally {
    await client.close();
  }
}

async function handleMDBDocumentQueryByIdRequest(clientReq, clientResp) {
  const client = new MongoClient(mdb_uri);
  try {
    var url_parts = url.parse(clientReq.url, true);

    const db_name = url_parts.query.db;
    const collection = url_parts.query.collection;
    const oid = ObjectID(url_parts.query.oid);

      switch(clientReq.method) {
          case "GET":
              await client.connect();
              const database = client.db(db_name);
              const col = database.collection(collection);

              var res = await col.findOne({_id: oid })
              if (res == null) {
                clientResp.writeHead(404, {'Content-Type': 'text/plain'});
                clientResp.end();
              } else {
                 clientResp.writeHead(200, {'Content-Type': 'application/json'});
                 clientResp.end(JSON.stringify(res));
              }
      }

  } catch (err) {
     console.error(err)
     clientResp.writeHead(500, {'Content-Type': 'text/plain'});
     clientResp.end();
  } finally {
    await client.close();
  }
}

async function handleMDBDocumentDeleteRequest(clientReq, clientResp)
{
    const client = new MongoClient(mdb_uri);
    try {
      var url_parts = url.parse(clientReq.url, true);

      const db_name = url_parts.query.db;
      const collection = url_parts.query.collection;
      const query = parser(url_parts.query.query);
        switch(clientReq.method) {
            case "GET":
                await client.connect();
                const database = client.db(db_name);
                const col = database.collection(collection);
                var res = await col.deleteMany(query)
                if (res == null) {
                  clientResp.writeHead(404, {'Content-Type': 'text/plain'});
                  clientResp.end();
                } else {
                   clientResp.writeHead(200, {'Content-Type': 'application/json'});
                   clientResp.end(JSON.stringify(res));
                }
        }

    } catch (err) {
       console.error(err)
       clientResp.writeHead(500, {'Content-Type': 'text/plain'});
       clientResp.end();
    } finally {
      await client.close();
    }
}

//Create and start the Http server
const PORT = 8888;
var applicationIds = {}  // Should be updated by the Docker setup script before any tests are run.
var server = http.createServer(function(req, resp) {
    try {
        winston.info('command-server: ' + req.method + " " + req.url);
        if (req.url.includes("/okhttp")) {
            handleOkHttp(req, resp);
        } else if (req.url.includes('/testapp1')) {
            handleApplicationId('testapp1', req, resp);
        } else if (req.url.includes('/testapp2')) {
            handleApplicationId('testapp2', req, resp);
        } else if (req.url.includes('/testapp3')) {
            handleApplicationId('testapp3', req, resp);
        } else if (req.url.includes('/watcher')) {
            handleWatcher(req, resp);
        } else if (req.url.includes('/forward-as-patch')) {
            handleForwardPatchRequest(req, resp);
        } else if (req.url.includes("/insert-document")) {
            handleMDBDocumentInsertRequest(req, resp);
        } else if (req.url.includes("/query-document-by-id")) {
            handleMDBDocumentQueryByIdRequest(req, resp);
        } else if (req.url.includes("/delete-document")) {
            handleMDBDocumentDeleteRequest(req, resp);
        } else {
            handleUnknownEndPoint(req, resp);
        }
    } catch(err) {
        winston.error('command-server: ' + err);
    }
});
server.listen(PORT, function() {
    winston.info("command-server: MongoDB Realm Integration Test Server listening on: 127.0.0.1:%s", PORT);
});
