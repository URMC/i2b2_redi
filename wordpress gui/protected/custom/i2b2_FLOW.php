<?php

	//show the top level tree, if there's nothing in the box, then show that.
	//look to see the tree level, this should defer to the secondary lookup PHP page to perform the view.
	//show a search box, this should trigger the middle part to update

	echo( "
		<table style='padding:0;'>
			<tr>
				<td>Enter Term or navigate below:<td>
				<td><input type='text' id='i2b2_FLOW_SEARCH' size=25 onKeyPress=\"
						if (event.keyCode == 13) {
							$('#i2b2_FLOW_PICKLIST').html($('#i2b2_FLOW_WAITING').html());
							$('#i2b2_FLOW_PICKLIST').load('custom/i2b2_FLOW_SEARCH.php?DETAIL=' + $('#i2b2_FLOW_SEARCH').val() );
							return false;
						}
					\"
				></td>
				<td><input type='button' value='Search'
					onClick=\"
						$('#i2b2_FLOW_PICKLIST').html($('#i2b2_FLOW_WAITING').html());
						$('#i2b2_FLOW_PICKLIST').load('custom/i2b2_FLOW_SEARCH.php?DETAIL=' + $('#i2b2_FLOW_SEARCH').val() );
					\"

				>
				</td>
			</tr>
		</table>
	");

	echo("<div id='i2b2_FLOW_WAITING' style='display:none;'><table width='100%' height='400' style='background-color:gray;'><tr><td><h1>Searching!</h1></td></tr></table></div>");
	echo("<div id='i2b2_FLOW_PICKLIST' style='overflow-y: auto; height:400px;'></div>");

	//then there should be an add button.
	echo("<div id='i2b2_FLOW_SELECTED'></div>");


	//then iterate through the naked FLT #'s,


	//Then show just the naked items.
	print_r( $selected_item_options );
?>