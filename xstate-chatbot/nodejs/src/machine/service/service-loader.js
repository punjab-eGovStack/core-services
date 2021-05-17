const config = require('../../env-variables');

if(config.serviceProvider == 'Dummy') {
    module.exports.personService = require('./dummy-person-service');
    module.exports.vitalsService = require('./dummy-vitals-service');
} else {
    module.exports.personService = require('./cova-person-service');
    module.exports.vitalsService = require('./cova-vitals-service');
}