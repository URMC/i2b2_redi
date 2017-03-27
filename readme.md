![University of Rochester Logo; Meliora.](/docs/images/logo.png)
# I2B2, Redcap, Excel, Data-Dictionary Integrations
Welcome to our i2b2 integration system, developed at the University of Rochester.
In this document you will find information of how data is structured differently at our institution, how to install the tools, what items need customization, and a general understanding of the utilities supplied by the system.
In short, our integration work is as follows:
* i2b2 communicates with an external schema that stores jobs, logs, study specific data
* This external schema supports a Wordpress based web site that allows for 
** study management, 
** single-sign-on capability, 
** data mart creation and management
** patient list management and re-identification 
** administrative features.
* Another external schema that supports EAV file storage for analysis and staging space for dynamic data pulls after pivoting data,

# Intended Audience
This document was written for an i2b2 systems administrator/developer interested in leveraging URMC's REDCap and other integration tools within their institutions.

# Design Decision / Action Item
We at URMC have decided to provision our i2b2 system in a slightly different way then other institutions. Within i2b2, there are already existing web administration tools to add projects and to grant access. We have opted not to utilize this functionality. 
The chief rationale behind this decision is that the i2b2 native functionality does not offer single sign on capability, nor did it offer LDAP authentication in the previous 1.6 version that this project was originally developed on. In addition to those limitations, there was limited capability to automate data mart / project creation. To remedy this, URMC created several key tools, which allow for an administrative user to create new isolated i2b2 installations that are tied to the main i2b2 installation via a series of views and synonyms. This reduces data duplication to an absolute minimum. This provisioning system also addressed the need for a DBA to manually create and load the data marts with the base i2b2 installation structure. In addition, this method puts all of these links into a single i2b2 data mart schema, reducing the overall need for schema pollution for system administrators. 
Another decision point we have done at URMC is that our i2b2 instance is not backed-up because it contains derived data that is routinely regenerated. Thus for data that is user generated, such as mappings or the identified lists of data, we keep that segregated from i2b2 so that this data is not available from that database. That data is also backed up for disaster recovery for the ability to regenerate the data marts and settings if needed. This also allowed for us to encrypt and otherwise patrol access to protected datamarts and to segregate them from deidentifed data.
This is described in the diagram below.
![Master Systems Diagram](/docs/images/msd.png)

Our tool set is capable of running without these integrations for purposes of REDCap and Excel extractions. Your institution may choose not to implement data mart provisioning in this manner. The immediate drawbacks to this design is as follows:
* A single Java service running DBA commands, modifying i2b2 running xml files.
** None of our data marts or customers have access to our database access, they cannot see other studies' data. 
** All data is funneled into the standard i2b2 observation structure (observation_fact) or local_observation_fact, which can reside on a different server.