var exec = require('cordova/exec');

exports.exist = function (arg0, success, error) {
    exec(success, error, 'Cifs', 'exist', [arg0]);
};

exports.dir = function (arg0, success, error) {
    exec(success, error, 'Cifs', 'dir', [arg0]);
};

exports.getfiles = function (arg0, success, error) {
    exec(success, error, 'Cifs', 'getfiles', [arg0]);
};

exports.download = function (arg0, success, error) {
    exec(success, error, 'Cifs', 'download', [arg0]);
};
