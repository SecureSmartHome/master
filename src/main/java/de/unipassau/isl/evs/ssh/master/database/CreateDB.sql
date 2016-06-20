CREATE TABLE UserDevice (
  _ID         INTEGER NOT NULL PRIMARY KEY,
  name        VARCHAR NOT NULL UNIQUE,
  fingerprint VARCHAR NOT NULL UNIQUE,
  GroupId     INTEGER NOT NULL,
  FOREIGN KEY (GroupId) REFERENCES DeviceGroup (_ID)
);
CREATE TABLE Permission (
  _ID                INTEGER NOT NULL PRIMARY KEY,
  name               VARCHAR NOT NULL,
  electronicModuleId INTEGER,
  UNIQUE (name, electronicModuleId),
  FOREIGN KEY (electronicModuleId) REFERENCES ElectronicModule (_ID) ON DELETE CASCADE
);
CREATE TABLE has_permission (
  permissionId INTEGER NOT NULL,
  userId       INTEGER NOT NULL,
  PRIMARY KEY (permissionId, userId),
  FOREIGN KEY (userId) REFERENCES UserDevice (_ID) ON DELETE CASCADE,
  FOREIGN KEY (permissionId) REFERENCES Permission (_ID) ON DELETE CASCADE
);
CREATE TABLE DeviceGroup (
  _ID                  INTEGER NOT NULL PRIMARY KEY,
  name                 VARCHAR NOT NULL UNIQUE,
  permissionTemplateId INTEGER NOT NULL,
  FOREIGN KEY (permissionTemplateId) REFERENCES PermissionTemplate (_ID)
);
CREATE TABLE PermissionTemplate (
  _ID  INTEGER NOT NULL PRIMARY KEY,
  name VARCHAR NOT NULL UNIQUE
);
CREATE TABLE composed_of_permission (
  permissionId         INTEGER NOT NULL,
  permissionTemplateId INTEGER NOT NULL,
  PRIMARY KEY (permissionId, permissionTemplateId),
  FOREIGN KEY (permissionTemplateId) REFERENCES PermissionTemplate (_ID) ON DELETE CASCADE,
  FOREIGN KEY (permissionId) REFERENCES Permission (_ID) ON DELETE CASCADE
);
CREATE TABLE ElectronicModule (
  _ID          INTEGER NOT NULL PRIMARY KEY,
  slaveId      INTEGER NOT NULL,
  name         VARCHAR NOT NULL UNIQUE,
  gpioPin      INTEGER,
  usbPort      INTEGER,
  wlanPort     INTEGER,
  wlanUsername VARCHAR,
  wlanPassword VARCHAR,
  wlanIP       VARCHAR,
  moduleType   VARCHAR NOT NULL,
  type         VARCHAR CHECK (
    type = 'MOCK' OR
    type = 'GPIO' OR
    type = 'USB' OR
    type = 'WLAN'),
  FOREIGN KEY (slaveId) REFERENCES Slave (_ID)
);
CREATE TABLE Slave (
  _ID         INTEGER NOT NULL PRIMARY KEY,
  name        VARCHAR NOT NULL UNIQUE,
  fingerprint VARCHAR NOT NULL UNIQUE
);
CREATE TABLE HolidayLog (
  _ID                INTEGER NOT NULL PRIMARY KEY,
  electronicModuleId INTEGER,
  action             VARCHAR NOT NULL,
  timestamp          INTEGER NOT NULL,
  FOREIGN KEY (electronicModuleId) REFERENCES ElectronicModule (_ID) ON DELETE CASCADE
);
