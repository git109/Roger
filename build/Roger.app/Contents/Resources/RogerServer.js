var sys = require("util"),
net = require("net"),  
http = require("http"),  
path = require("path"),  
dgram = require("dgram"),  
url = require("url"),  
filesys = require("fs"),
os = require("os");;

port = 8081;
mobilePort = 8082;
multicastPort = 8099;

hostname = process.argv[2];
multicast = process.argv[3];
sys.puts("address for server: " + hostname);
sys.puts("multicast address for server: " + multicast);
var clients = [];
var files = {};
var fileIndex = 0;

Array.prototype.remove = function(e) {
    for (var i = 0; i < this.length; i++) {
        if (e == this[i]) { return this.splice(i, 1); }
    }
};

http.createServer(function(request, response){  
    var parts = url.parse(request.url, true);
    sys.puts("got path: " + parts.pathname + " query: " + parts.query);

    if (parts.pathname == "/post") {
        // Desktop client is posting a file
        var apk = parts.query['apk'];
        var layout = parts.query['layout'];
		var pack = parts.query['pack'];
		var minSdk = parts.query['minSdk'];
        sys.puts("posting apk: " + apk + " layout: " + layout);
        response.writeHeader(200);
		response.end();

		fileIndex++;
		files[fileIndex] = apk;
        clients.forEach(function(s) {
			sys.puts("sending data to a client");
			s.write(layout + "\n" + fileIndex + "\n" + pack + "\n" + minSdk + "--", "utf8");
			s.end();
        });  
    } else if (parts.pathname == "/get") {
        var hash = parts.query['hash'];
        var fileName = files[hash];

        var bufSize = 64 * 1024;
        var chunkSize = 64 * 1024;
        var pos = 0;
        var buffer = new Buffer(bufSize);
        sys.puts("pos: " + pos + " bufSize: " + bufSize);

        sys.puts("sending " + fileName + " to a client");
        response.writeHead(200, {
            'Transfer-Encoding' : 'chunked',
            'Content-Encoding' : 'application/octet-stream'
        });

        var readStream = filesys.createReadStream(fileName);
        sys.pump(readStream, response, function (err) {
            if (err) {
                sys.puts("error writing " + fileName + " to client: " + err);
            }
        });
    }
}).listen(port);  
sys.puts("Server Running on " + port);

var server = net.createServer(function (stream) {
    clients.push(stream);

    stream.setTimeout(0);
    stream.setEncoding("utf8");

    stream.addListener("connect", function () {
        sys.puts("Added mobile client. Total: " + clients.length);
        stream.pipe(stream);
    });

    stream.addListener("end", function() {
        clients.remove(stream);
        sys.puts("Removed mobile client. Total: " + clients.length);
        stream.end();
    });
});
server.listen(mobilePort, hostname);
sys.puts("Mobile server Running on " + hostname + ":" + mobilePort);

// setup server discovery listener
var udpSocket = dgram.createSocket("udp4");
udpSocket.on("message", function(msg, rinfo) {
    // send back a response
    sys.puts("received something from address: " + rinfo.address);
	sys.puts("Host name " + os.hostname());

    var message = new Buffer("SECRETS!" + os.hostname());
    udpSocket.send(message, 0, message.length, multicastPort, rinfo.address, function (err, bytes) {
        sys.puts("error sending udp message: " + err);
    });
});

udpSocket.bind(multicastPort);
udpSocket.setBroadcast(true);
udpSocket.addMembership(multicast);


