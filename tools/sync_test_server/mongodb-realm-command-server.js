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
const fs = require('fs')

const isPortAvailable = require('is-port-available');

function handleUnknownEndPoint(req, resp) {
    resp.writeHead(404, {'Content-Type': 'text/plain'});
    resp.end();
}

function handleOkHttp(req, resp) {
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
        var urlParts = url.parse(forwardUrl, false);

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
        } else if (req.url.includes('/watcher')) {
            handleWatcher(req, resp);
        } else if (req.url.includes('/forward-as-patch')) {
            handleForwardPatchRequest(req, resp);
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
