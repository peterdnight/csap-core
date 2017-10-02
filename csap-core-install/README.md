# Overview
CSAP can be installed on any linux host (VmWare, Openstack, AWS, physical hardware, etc.). The majority of existing
hosts are using Centos or Redhat enterprise, so some tweaking in the installation scripts and parsers might be required
if other variants are used. New variants are generally very easy to correct - simply open an issue to track if needed. 


### New Application
Typical install for creating a new application. New applications typically require more setup time as company wide settings
(such as LDAP, Maven, etc. providers are defined). Typically a toolshost is created (by ommitting -clone), which then enables
all applications in your company to clone the settings:

```bash
./dist/install.sh -noPrompt  -installCsap 25  -toolsServer csaptools.yourcompany.com \
 	-starterUrl "http://csaptools.yourcompany.com/admin/os/getConfigZip?path=YourStarter"
```
 	
### Adding a host
typical install for new host in existing application
```bash 
 ./dist/install.sh -noPrompt  -installCsap 25  -toolsServer csaptools.yourcompany.com \
 	-clone existingHost.yourcompany.com
```

### Options:
```
# Options:
#   -noPrompt               # skip past all confirmation prompts
#   -installDisk xxx        # volume group device: default is /dev/sdb . To use root partition: use default
#   -installCsap <size>     # agent disk size in GB ; where services are installed/run
#   -clone <option>         # Optional: hostname 
# 	-starterUrl <url>		# Optional: use url to retrieve a base configuration for new applications.
#   -allInOne               # Optional: Uses -full install package from http://csaptools.cisco.com/csap/
#   -skipKernel             # Optional: skips kernel configuration and os package updates
#   -zone "America/Chicago" # Optional: configures timezone
#   -fsType <type>          # Optional: default ext4, xfs
#   -toolsServer <server>   # default: none (uses full installer) Optional: csaptools.cisco.com , rtptools-prd01.cisco.com
#   -extraDisk /data <size> # Optional: creates disk mount location in volume group; useful for db disks
#   -installActiveMq <size> # Optional: create mqUser account with disk size in GB, recommend: 10
#   -installOracle <size>   # Optional: create oracle account with disk size in GB, recommend: 200
#
```

### Examples

#### Getting the Installer

Typically run as root to leverage core configuration automation (kernel settings, file system creation, etc.). 
The installer can be run as a regular user, but then host configuration will need to be applied as part of a separate process.

```
# Optional: centos firewall rules
systemctl mask firewalld.service; systemctl disable firewalld.service ;systemctl stop firewalld.service ; systemctl status firewalld.service
 
# get installer; if needed: yum -y install unzip wget
echo == cleaning up previous installs
cd $HOME; \rm -rf *;
wget http://maven.yourcompany.com/artifactory/your-group/org/csap/csap-core-install/6.0.0/csap-core-install-6.0.0.zip
unzip Csap*.zip
```


#### Tools Server or 1st Host
```bash

# Simple install sets up a vanilla server eg. a tool host with default application definition
./dist/install.sh -noPrompt  -installCsap 25  -toolsServer csaptools.yourcompany.com \
    -mavenRepoUrl http://maven.yourcompany.com/artifactory/your-group \
    -mavenSettingsUrl http://csaptools.yourcompany.com/cisco/settings.xml
 
# Once a basic application definition has been setup using CSAP Editor, additional hosts are added using clone
./dist/install.sh -noPrompt  -installCsap 25  -toolsServer csaptools.yourcompany.com -clone <YOUR_FIRST_VM>
```



#### Tools Server or 1st Host














