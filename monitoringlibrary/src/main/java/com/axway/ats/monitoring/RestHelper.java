/*
 * Copyright 2017 Axway Software
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.axway.ats.monitoring;

import java.io.File;
import java.io.IOException;

import com.axway.ats.action.rest.RestClient;
import com.axway.ats.action.rest.RestClient.RESTDebugLevel;
import com.axway.ats.agent.core.context.ApplicationContext;
import com.axway.ats.common.systemproperties.AtsSystemProperties;
import com.axway.ats.core.filesystem.LocalFileSystemOperations;
import com.axway.ats.core.utils.IoUtils;
import com.axway.ats.action.rest.RestMediaType;
import com.axway.ats.action.rest.RestResponse;

/**
 * This class is used to keep track of information, needed for each monitoring
 * host, such as ATS_UID, RestClient object and Agent IP
 */
public class RestHelper {

	public static final String BASE_MONITORING_REST_SERVICE_URI = "/agentapp/restservice/monitoring";

	public static final String BASE_CONFIGURATION_REST_SERVICE_URI = "/agentapp/restservice/configuration";

	public static final String INITIALIZE_DB_CONNECTION_RELATIVE_URI = "/initializeDbConnection";

	public static final String JOIN_TESTCASE_RELATIVE_URI = "/joinTestcase";

	public static final String INITIALIZE_MONITORING_RELATIVE_URI = "/initializeMonitoring";

	public static final String SCHEDULE_SYSTEM_MONITORING_RELATIVE_URI = "/scheduleSystemMonitoring";

	public static final String SCHEDULE_MONITORING_RELATIVE_URI = "/scheduleMonitoring";

	public static final String SCHEDULE_PROCESS_MONITORING_RELATIVE_URI = "/scheduleProcessMonitoring";

	public static final String SCHEDULE_CHILD_PROCESS_MONITORING_RELATIVE_URI = "/scheduleChildProcessMonitoring";

	public static final String SCHEDULE_JVM_MONITORING_RELATIVE_URI = "/scheduleJvmMonitoring";

	public static final String SCHEDULE_CUSTOM_JVM_MONITORING_RELATIVE_URI = "/scheduleCustomJvmMonitoring";

	public static final String SCHEDULE_USER_ACTIVITY_RELATIVE_URI = "/scheduleUserActivity";

	public static final String START_MONITORING_RELATIVE_URI = "/startMonitoring";

	public static final String STOP_MONITORING_RELATIVE_URI = "/stopMonitoring";

	public static final String LEAVE_TESTCASE_RELATIVE_URI = "/leaveTestcase";

	public static final String DEINITIALIZE_DB_CONNECTION_RELATIVE_URI = "/deinitializeDbConnection";

	private RestClient restClient;
	private String uid;

	public RestHelper() {
	}

	public RestResponse post(String atsAgentIp, String baseRestUri, String relativeRestUri, Object[] values) {

		RestResponse response = null;

		initializeRestClient(atsAgentIp, baseRestUri, relativeRestUri);

		String jsonBody = null;

		if (relativeRestUri.endsWith(INITIALIZE_DB_CONNECTION_RELATIVE_URI)) {
			jsonBody = JsonMonitoringUtils.constructInitializeDbConnectionJson(values);
		} else if (relativeRestUri.endsWith(JOIN_TESTCASE_RELATIVE_URI)) {
			jsonBody = JsonMonitoringUtils.constructJoinTestcaseJson(values);
		} else if (relativeRestUri.endsWith(INITIALIZE_MONITORING_RELATIVE_URI)) {
			jsonBody = JsonMonitoringUtils.constructInitializeMonitoringJson(values);
		} else if (relativeRestUri.endsWith(SCHEDULE_SYSTEM_MONITORING_RELATIVE_URI)) {
			jsonBody = JsonMonitoringUtils.constructScheduleSystemMonitoringJson(values);
		} else if (relativeRestUri.endsWith(SCHEDULE_MONITORING_RELATIVE_URI)) {
			jsonBody = JsonMonitoringUtils.constructScheduleMonitoringJson(values);
		} else if (relativeRestUri.endsWith(SCHEDULE_PROCESS_MONITORING_RELATIVE_URI)) {
			jsonBody = JsonMonitoringUtils.constructScheduleProcessMonitoringJson(values);
		} else if (relativeRestUri.endsWith(SCHEDULE_CHILD_PROCESS_MONITORING_RELATIVE_URI)) {
			jsonBody = JsonMonitoringUtils.constructScheduleProcessMonitoringJson(values);
		} else if (relativeRestUri.endsWith(SCHEDULE_JVM_MONITORING_RELATIVE_URI)) {
			jsonBody = JsonMonitoringUtils.constructScheduleJvmProcessMonitoringJson(values);
		} else if (relativeRestUri.endsWith(SCHEDULE_CUSTOM_JVM_MONITORING_RELATIVE_URI)) {
			jsonBody = JsonMonitoringUtils.constructScheduleCustomJvmProcessMonitoringJson(values);
		} else if (relativeRestUri.endsWith(SCHEDULE_USER_ACTIVITY_RELATIVE_URI)) {
			jsonBody = JsonMonitoringUtils.constructScheduleUserActivityJson(values);
		} else if (relativeRestUri.endsWith(START_MONITORING_RELATIVE_URI)) {
			jsonBody = JsonMonitoringUtils.constructStartMonitoringJson(values);
		} else if (relativeRestUri.endsWith(STOP_MONITORING_RELATIVE_URI)) {
			jsonBody = JsonMonitoringUtils.constructStopMonitoringJson(values);
		} else if (relativeRestUri.endsWith(LEAVE_TESTCASE_RELATIVE_URI)) {
			jsonBody = JsonMonitoringUtils.constructLeaveTestcaseJson(values);
		} else if (relativeRestUri.endsWith(DEINITIALIZE_DB_CONNECTION_RELATIVE_URI)) {
			jsonBody = JsonMonitoringUtils.constructDeinitializeDbConnectionJson(values);
		} else {
			throw new IllegalArgumentException(
					"relativeRestUri does not lead to existing REST method. Please consult the documentation.");
		}

		response = this.restClient.postObject(jsonBody);

		if (relativeRestUri.endsWith(INITIALIZE_DB_CONNECTION_RELATIVE_URI)) {
			this.uid = response.getBodyAsJson().getString(ApplicationContext.ATS_UID_SESSION_TOKEN);
			synchronizeUidWithLocalOne();
		}

		return response;

	}

	/**
	 * Since we want both AgentService and RestService (both are located on the
	 * agent) to share the same ATS UID, we save the returned uid to the file,
	 * so when any future connection is executed. regardless via REST or Web
	 * Service, the same uid will be used for both, as long as the user and the
	 * Java project's directory are the same
	 * 
	 * @throws IOException
	 */
	private void synchronizeUidWithLocalOne() {

		// create temp file containing caller working directory and the unique
		// id
		String userWorkingDirectory = AtsSystemProperties.SYSTEM_USER_HOME_DIR;
		String uuiFileLocation = AtsSystemProperties.SYSTEM_USER_TEMP_DIR + AtsSystemProperties.SYSTEM_FILE_SEPARATOR
				+ "\\ats_uid.txt";
		File uuiFile = new File(uuiFileLocation);

		// check if the file exist and if exist check if the data we need is in,
		// otherwise add it to the file
		try {
			if (uuiFile.exists()) {
				// the file already exists
				String uuiFileContent = IoUtils.streamToString(IoUtils.readFile(uuiFileLocation));
				if (uuiFileContent.contains(userWorkingDirectory)) {
					// there is already saved UID in the file for the current IP
					// and java project's directory
					for (String line : uuiFileContent.split("\n")) {
						if (line.contains(userWorkingDirectory)) {
							this.uid = line.substring(userWorkingDirectory.length()).trim();
						}
					}
				} else {
					/*
					 * the file does not contains UID for the current IP and
					 * Java project's directory, so save the received one, if it
					 * is not null
					 */
					if (this.uid != null) {
						new LocalFileSystemOperations().appendToFile(uuiFileLocation,
								userWorkingDirectory + "\t" + this.uid + "\n");
					}
				}
			} else {
				/*
				 * the file does NOT exists, so create and save the received
				 * UID, if it is not null
				 */
				if (this.uid != null) {
					uuiFile.createNewFile();
					if (uuiFile.exists()) {
						new LocalFileSystemOperations().appendToFile(uuiFileLocation,
								userWorkingDirectory + "\t" + this.uid + "\n");
					}
				}
			}
		} catch (Exception e) {
			// log a warning
		}
	}

	/**
	 * Create RestClient instance
	 * 
	 * @param atsAgentIp
	 *            the IP of the agent, on which we want to perform some
	 *            monitoring operation
	 * @param relativeRestUri
	 *            the relative URI for the desired monitoring operation
	 */
	public void initializeRestClient(String atsAgentIp, String baseRestUri, String relativeRestUri) {

		// create RestClient instance
		this.restClient = new RestClient("http://" + atsAgentIp + baseRestUri + relativeRestUri);
		// disable any logging (both REST headers and body)
		this.restClient.setVerboseMode(RESTDebugLevel.NONE);
		this.restClient.setRequestMediaType(RestMediaType.APPLICATION_JSON);
		this.restClient.setResponseMediaType(RestMediaType.APPLICATION_JSON);
		// set ATS_UID header
		synchronizeUidWithLocalOne();
		this.restClient.addRequestHeader(ApplicationContext.ATS_UID_SESSION_TOKEN, this.uid);
	}

	public void disconnect() {

		if (this.restClient != null) {
			this.restClient.disconnect();
		} else {
			throw new RuntimeException("Could not disconnect, because RestClient is not initialized.");
		}

	}

}
