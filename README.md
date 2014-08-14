WaspmoteGateway
===============
Gateway for connecting of Waspmote nodes via 802.15.4/ZigBee or TCP to OpenHAB.

To connect 802.15.4 / ZigBee modules to the gateway a serial adapter is needed (eg. USB-UART converter). A data frame that is sent from Waspmote nodes must be in the following format: 
- <=>#OPENHAB_ITEM_1:VALUE_1#OPENHAB_ITEM_2:VALUE_2#

where OPENHAB_ITEM_1 is name of item defined in OpenHAB items configuration and VALUE_1 is corresponding value. You can send as many of the items as can fit into 802.15.4/ZigBee or TCP payload (e.g. for 802.15.4 maximum payload length is 102 bytes). Item-value pairs are separated with '#' delimiter, and value is separated from key (item name) with ':' delimiter. Data frame must begin with '<=>#'. Waspmote example code is given in "waspmote_examples" folder.

License
=======
This source code is available under the MIT license.

Contact
=======
- Author: Enio Kaljić
- E-mail: enio.kaljic@etf.unsa.ba
