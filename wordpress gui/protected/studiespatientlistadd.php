<?php require_once("../common.php");?>
<?php $_ENC = decryptGetMethod(); ?>
<?php if( isLoggedIn() && isset($_ENC["SYSID"]) ){

	$pest = $DB->query( "INSERT INTO ENROLLED_PATIENT (SYSID, PROJECTID) VALUES (SYSIDS.NEXTVAL,'".intval(f($_ENC["SYSID"]))."') " );

}?>