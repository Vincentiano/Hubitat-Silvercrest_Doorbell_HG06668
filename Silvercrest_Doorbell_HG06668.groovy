/*
	Silvercrest Doorbell HG06668 
*/

import groovy.transform.Field

@Field Map diagAttributes = [
    	"0000":["name":"ResetCount","val":0x0000],
    	"0104":["name":"TXRetrys","val":0x0104],
    	"0105":["name":"TXFails","val":0x0105],
    	"011A":["name":"PacketDrops","val":0x011A],
    	"0115":["name":"DecryptFailures","val":0x0115],
    	"011D":["name":"RSSI","val":0x011D],
    	"011E":["name":"Parent","val":0x011E],
    	"011F":["name":"Children","val":0x011F],
    	"0120":["name":"Neighbors","val":0x0120]
    ]

metadata {
    definition (name: "Silvercrest Doorbell HG06668", namespace: "Vincentiano", author: "Vincent van Didden") {
	capability "Configuration"
	capability "Refresh"
	capability "Battery"
    capability "TamperAlert"
	capability "PushableButton"
	capability "Switch"

   fingerprint profileId: "0260", endpointId:"01", device_type: "0402", inClusters: "0000,0001,0003,0500,0B05", outClusters:"0019", manufacturer: "_TZ1800_ladpngdx", model: "TS0211", class: "zigpy.device.Device", deviceJoinName: "Silvercrest Doorbell HG06668"
/*
Manufacturer: _TZ1800_ladpngdx
Product Name: Silvercrest Doorbell HG06668
Model Number: TS0211
deviceTypeId: 0x0402
manufacturer: Heiman
*/
	}
        
    preferences {
        //standard logging options
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
    }
}

def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def parse(String description) {
	if (logEnable) log.debug "description is ${description}"
	if (description.startsWith("catchall")) return
    if (description.startsWith("zone status")){
        def zs = zigbee.parseZoneStatus(description) 
        if(zs.tamper == 1){
            log.warn "Doorbell Tamper Detected"
         setTamperAlert(1)   
        }else if(device.currentValue("tamper") == "detected"){
            log.warn "Doorbell Tamper Cleared"
            setTamperAlert(0)
        }
        readButtonState("0001") //Trigger Doorbell, so tamper will be notified
        return
    }
    def descMap = zigbee.parseDescriptionAsMap(description)
    if (logEnable) log.debug "descMap:${descMap}"
	
	def cluster = descMap.cluster
	def hexValue = descMap.value
	def attrId = descMap.attrId
	
	switch (cluster){
		case "0000" :	//basic
            if (logEnable) log.debug "Basic is ${hexValue}"
			break
		case "0001" :	//Power Config
            if(attrId == "0021"){
                readBatteryState(hex)
            }else{
                if (logEnable) log.debug "Power Config is ${hexValue}"
                if (logEnable) log.debug "Power Config attr is ${attrId}"
            }
			break
		case "0003" :	//Identify
			if (logEnable) log.debug "Identify is ${hexValue}"
            if (logEnable) log.debug "Identify attr is ${attrId}"
			break
		case "0500" :	//Button
			readButtonState(hexValue)
			break
		case "0B05" : //diag
        	if (logEnable) log.warn "attrId:${attrId}, hexValue:${hexValue}"
        	def value = hexStrToUnsignedInt(hexValue)
        	log.warn "diag- ${diagAttributes."${attrId}".name}:${value} "
			break
		default :
			log.warn "skipped cluster: ${cluster}, descMap:${descMap}"
			break
	}
	return
}

//event methods
private readButtonState(hex){
    def value = hexStrToSignedInt(hex)
    def name = "pushed"
    def descriptionText = "${device.displayName} ${name} is ${value}"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name: name,value: value, isStateChange:true,descriptionText: descriptionText)
}

private readBatteryState(hex){
    def rawValue = hexStrToSignedInt(hex)
    def value = Math.round(rawValue/255.0*100.0)
    def name = "battery"
    def descriptionText = "${device.displayName} ${name} is ${value} %"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name: name,value: value, isStateChange:true,descriptionText: descriptionText)
}

private setTamperAlert(val){
    def name = "tamper"
    def value = val? "detected":"clear"
    def descriptionText = "${device.displayName} ${name} is ${value}"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name: name,value: value, isStateChange:true,descriptionText: descriptionText)
}

//capability and device methods
def off() {
    zigbee.off()
}

def on() {
    zigbee.on()
}

def refresh() {
    log.debug "Refresh"
    
	//readAttribute(cluster,attribute,mfg code,optional delay ms)
    def cmds = zigbee.readAttribute(0x0500,0x0000,[:],200)		//temp
    	diagAttributes.each{ it ->
            //log.debug "it:${it.value.val}"
			cmds +=  zigbee.readAttribute(0x0B05,it.value.val,[:],200) 
		}  
    log.info "cmds:${cmds}"
    return cmds
}

def configure() {
    log.debug "Configuring Reporting and Bindings."
    runIn(1800,logsOff)
    
    List cmds = zigbee.configureReporting(0x0001, 0x0020, DataType.UINT16, 30, 300, 0x01)
    cmds += zigbee.configureReporting(0x0003, 0x0000, DataType.UINT16, 1, 21600, 1)	//power
    cmds += zigbee.configureReporting(0x0500, 0x0000, DataType.UINT16, 1, 21600, 1)	//button
    cmds += refresh()
    log.info "cmds:${cmds}"
    return cmds
}

def updated() {
    log.trace "Updated()"
    log.warn "debug logging is: ${logEnable == true}"
    log.warn "description logging is: ${txtEnable == true}"
    if (logEnable) runIn(1800,logsOff)    
}