#--------------------------------------
# USE:
#  The diffs are ordered by datamodel version number.
#--------------------------------------

DROP PROCEDURE IF EXISTS update_user_password;
DROP PROCEDURE IF EXISTS insert_patient_stub;
DROP PROCEDURE IF EXISTS insert_user_stub;


#----------------------------------------
# OpenMRS Datamodel version 1.1.01
# Ben Wolfe                 May 31st 2007
# Adding township_division, region,  and 
# subregion attributes to patient_address 
# and location tables
#----------------------------------------

DROP PROCEDURE IF EXISTS diff_procedure;

delimiter //

CREATE PROCEDURE diff_procedure (IN new_db_version VARCHAR(10))
 BEGIN
	IF (SELECT REPLACE(property_value, '.', '0') < REPLACE(new_db_version, '.', '0') FROM global_property WHERE property = 'database_version') THEN
	SELECT CONCAT('Updating to ', new_db_version) AS 'Datamodel Update:' FROM dual;
	
	ALTER TABLE `person_address` ADD COLUMN `region` varchar(50) default NULL;
	ALTER TABLE `person_address` ADD COLUMN `subregion` varchar(50) default NULL;
	ALTER TABLE `person_address` ADD COLUMN `township_division` varchar(50) default NULL;
	
	ALTER TABLE `location` ADD COLUMN `region` varchar(50) default NULL;
	ALTER TABLE `location` ADD COLUMN `subregion` varchar(50) default NULL;
	ALTER TABLE `location` ADD COLUMN `township_division` varchar(50) default NULL;
	
	UPDATE `global_property` SET property_value=new_db_version WHERE property = 'database_version';
	
	END IF;
 END;
//

delimiter ;
call diff_procedure('1.1.10');


#-----------------------------------
# Clean up - Keep this section at the very bottom of diff script
#-----------------------------------

DROP PROCEDURE IF EXISTS diff_procedure;