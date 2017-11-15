package org.opencps.dossiermgt.scheduler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.opencps.auth.utils.APIDateTimeUtils;
import org.opencps.dossiermgt.action.DossierActions;
import org.opencps.dossiermgt.action.impl.DossierActionsImpl;
import org.opencps.dossiermgt.action.util.MultipartUtility;
import org.opencps.dossiermgt.constants.DossierTerm;
import org.opencps.dossiermgt.model.Dossier;
import org.opencps.dossiermgt.model.ProcessAction;
import org.opencps.dossiermgt.model.ProcessOption;
import org.opencps.dossiermgt.model.ServiceConfig;
import org.opencps.dossiermgt.model.ServiceProcess;
import org.opencps.dossiermgt.service.DossierLocalServiceUtil;
import org.opencps.dossiermgt.service.ProcessActionLocalServiceUtil;
import org.opencps.dossiermgt.service.ProcessOptionLocalServiceUtil;
import org.opencps.dossiermgt.service.ServiceConfigLocalServiceUtil;
import org.opencps.dossiermgt.service.ServiceProcessLocalServiceUtil;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;

import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONException;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.messaging.BaseSchedulerEntryMessageListener;
import com.liferay.portal.kernel.messaging.DestinationNames;
import com.liferay.portal.kernel.messaging.Message;
import com.liferay.portal.kernel.model.Company;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.module.framework.ModuleServiceLifecycle;
import com.liferay.portal.kernel.scheduler.SchedulerEngineHelper;
import com.liferay.portal.kernel.scheduler.TimeUnit;
import com.liferay.portal.kernel.scheduler.TriggerFactory;
import com.liferay.portal.kernel.scheduler.TriggerFactoryUtil;
import com.liferay.portal.kernel.search.Field;
import com.liferay.portal.kernel.service.CompanyLocalServiceUtil;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.UserLocalServiceUtil;
import com.liferay.portal.kernel.servlet.HttpMethods;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.PropsKeys;
import com.liferay.portal.kernel.util.PropsUtil;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.uuid.PortalUUIDUtil;

@Component(immediate = true, service = DossierPullScheduler.class)
public class DossierPullScheduler extends BaseSchedulerEntryMessageListener {
	private final String baseUrl = "http://localhost:8080/o/rest/v2/";
	private final String username = "test@liferay.com";
	private final String password = "test";
	private final String serectKey = "OPENCPSV2";
	private static final int BUFFER_SIZE = 4096;

	@Override
	protected void doReceive(Message message) throws Exception {

		_log.info("OpenCPS get DOSSIERS.....");

		Company company = CompanyLocalServiceUtil.getCompanyByMx(PropsUtil.get(PropsKeys.COMPANY_DEFAULT_WEB_ID));
		ServiceContext serviceContext = new ServiceContext();
		serviceContext.setCompanyId(company.getCompanyId());

		User systemUser = UserLocalServiceUtil.getUserByEmailAddress(company.getCompanyId(), username);

		try {
			String path = "dossiers?submitting=true&secetKey=" + serectKey;
			URL url = new URL(baseUrl + path);

			HttpURLConnection conn = (HttpURLConnection) url.openConnection();

			String authString = username + ":" + password;

			String authStringEnc = new String(Base64.getEncoder().encodeToString(authString.getBytes()));
			conn.setRequestProperty("Authorization", "Basic " + authStringEnc);

			conn.setRequestMethod("GET");
			conn.setRequestProperty("Accept", "application/json");

			if (conn.getResponseCode() != 200) {
				throw new RuntimeException("Failed : HTTP error code : " + conn.getResponseCode());
			}

			BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));

			String output;

			StringBuffer sb = new StringBuffer();

			while ((output = br.readLine()) != null) {
				sb.append(output);

			}

			JSONObject jsData = JSONFactoryUtil.createJSONObject(sb.toString());

			JSONArray array = JSONFactoryUtil.createJSONArray(jsData.getString("data"));

			for (int i = 0; i < array.length(); i++) {
				JSONObject object = array.getJSONObject(i);

				pullDossier(company, object, systemUser);
			}

			System.out.println(sb.toString());

			conn.disconnect();

		} catch (MalformedURLException e) {

			e.printStackTrace();
		} catch (IOException e) {

			e.printStackTrace();

		}

	}

	private void pullDossier(Company company, JSONObject object, User systemUser) throws PortalException {
		long dossierId = GetterUtil.getLong(object.get(DossierTerm.DOSSIER_ID));

		ServiceContext serviceContext = new ServiceContext();
		serviceContext.setCompanyId(company.getCompanyId());
		serviceContext.setUserId(object.getLong(DossierTerm.USER_ID));

		// Dossier dossier = DossierLocalServiceUtil.fetchDossier(dossierId);

		long sourceGroupId = object.getLong(Field.GROUP_ID);
		String referenceUid = object.getString(DossierTerm.REFERENCE_UID);

		// ServerConfig server =
		// ServerConfigLocalServiceUtil.getGroupId(sourceGroupId);

		String serverno = object.getString(DossierTerm.SERVER_NO);

		// if (Validator.isNotNull(server))
		// serverno = server.getServerNo();

		List<ServiceProcess> processes = ServiceProcessLocalServiceUtil.getByServerNo(serverno);

		List<ServiceProcess> syncProcesses = new ArrayList<ServiceProcess>();

		String serviceInfoCode = object.getString(DossierTerm.SERVICE_CODE);
		String govAgencyCode = object.getString(DossierTerm.GOV_AGENCY_CODE);
		String dossierTemplateCode = object.getString(DossierTerm.DOSSIER_TEMPLATE_NO);

		for (ServiceProcess process : processes) {

			long desGroupId = process.getGroupId();

			try {
				ServiceConfig config = ServiceConfigLocalServiceUtil.getBySICodeAndGAC(desGroupId, serviceInfoCode,
						govAgencyCode);

				ProcessOption option = null;

				if (Validator.isNotNull(config)) {
					option = ProcessOptionLocalServiceUtil.getByDTPLNoAndServiceCF(desGroupId, dossierTemplateCode,
							config.getServiceConfigId());
				}

				if (Validator.isNotNull(option)) {

					ServiceProcess desServiceProcess = ServiceProcessLocalServiceUtil
							.getServiceProcess(option.getServiceProcessId());

					syncProcesses.add(desServiceProcess);
				}

			} catch (Exception e) {
				_log.info("NOProcess");
			}

			// ProcessOption option = ProcessOptionLocalServiceUtil.get
		}

		for (ServiceProcess syncServiceProcess : syncProcesses) {
			// Create DOSSIER for destination PROCESS
			// Get AutoEvent
			// Call nextAction

			Dossier desDossier = DossierLocalServiceUtil.getByRef(syncServiceProcess.getGroupId(),
					object.getString(DossierTerm.REFERENCE_UID));

			if (Validator.isNull(desDossier)) {
				// Create DOSSIER

				long desGroupId = syncServiceProcess.getGroupId();

				desDossier = DossierLocalServiceUtil.updateDossier(desGroupId, 0l, referenceUid,
						object.getInt(DossierTerm.COUNTER), object.getString(DossierTerm.SERVICE_CODE),
						object.getString(DossierTerm.SERVICE_NAME), govAgencyCode,
						object.getString(DossierTerm.GOV_AGENCY_NAME), object.getString(DossierTerm.APPLICANT_NAME),
						object.getString(DossierTerm.APPLICANT_ID_TYPE), object.getString(DossierTerm.APPLICANT_ID_NO),
						APIDateTimeUtils.convertStringToDate(object.getString(DossierTerm.APPLICANT_ID_DATE),
								APIDateTimeUtils._NORMAL_PARTTERN),
						object.getString(DossierTerm.ADDRESS), object.getString(DossierTerm.CITY_CODE),
						object.getString(DossierTerm.CITY_NAME), object.getString(DossierTerm.DISTRICT_CODE),
						object.getString(DossierTerm.DISTRICT_NAME), object.getString(DossierTerm.WARD_CODE),
						object.getString(DossierTerm.WARD_NAME), object.getString(DossierTerm.CONTACT_NAME),
						object.getString(DossierTerm.CONTACT_TEL_NO), object.getString(DossierTerm.CONTACT_EMAIL),
						object.getString(DossierTerm.DOSSIER_TEMPLATE_NO), object.getString(DossierTerm.DOSSIER_NOTE),
						object.getString(DossierTerm.SUBMISSION_NOTE), object.getString(DossierTerm.APPLICANT_NOTE),
						object.getString(DossierTerm.BRIEF_NOTE), object.getString(DossierTerm.DOSSIER_NO), false,
						APIDateTimeUtils.convertStringToDate(object.getString(DossierTerm.CORRECTING_DATE),
								APIDateTimeUtils._NORMAL_PARTTERN),
						object.getString(DossierTerm.DOSSIER_STATUS), object.getString(DossierTerm.DOSSIER_STATUS_TEXT),
						object.getString(DossierTerm.DOSSIER_SUB_STATUS),
						object.getString(DossierTerm.DOSSIER_SUB_STATUS_TEXT), 0l, 0l,
						object.getInt(DossierTerm.VIA_POSTAL), object.getString(DossierTerm.POSTAL_ADDRESS),
						object.getString(DossierTerm.POSTAL_CITY_CODE), object.getString(DossierTerm.POSTAL_CITY_NAME),
						object.getString(DossierTerm.POSTAL_TEL_NO), dossierTemplateCode,
						object.getBoolean(DossierTerm.NOTIFICATION), object.getBoolean(DossierTerm.ONLINE),
						object.getString(DossierTerm.SERVER_NO), serviceContext);

				long desDossierId = desDossier.getPrimaryKey();

				// Process doAction (with autoEvent = SUBMIT)
				try {
					// DossierLocalServiceUtil.syncDossier(desDossier);

					ProcessAction processAction = ProcessActionLocalServiceUtil
							.fetchBySPI_PRESC_AEV(syncServiceProcess.getServiceProcessId(), StringPool.BLANK, "SUBMIT");

					DossierActions actions = new DossierActionsImpl();

					actions.doAction(syncServiceProcess.getGroupId(), desDossierId, desDossier.getReferenceUid(),
							processAction.getActionCode(), processAction.getProcessActionId(),
							systemUser.getFullName() + "_SYSTEM", "SYNC_ACTION_BY_SYSTEM", 0l, systemUser.getUserId(),
							serviceContext);

				} catch (Exception e) {
					_log.info("SyncDossierUnsuccessfuly" + desDossier.getReferenceUid());
				}

				// Update DossierFile

			} else {

				if (Validator.isNotNull(object.getString(DossierTerm.CANCELLING_DATE))) {
					// Update cancellingDate
					desDossier.setCancellingDate(APIDateTimeUtils.convertStringToDate(
							object.getString(DossierTerm.CANCELLING_DATE), APIDateTimeUtils._NORMAL_PARTTERN));
				}

				if (Validator.isNotNull(object.getString(DossierTerm.CORRECTING_DATE))) {
					// Update correctingDate
					desDossier.setCorrecttingDate(APIDateTimeUtils.convertStringToDate(
							object.getString(DossierTerm.CORRECTING_DATE), APIDateTimeUtils._NORMAL_PARTTERN));
				}

			}

			// TODO add sync DOSSIERFILE and PAYMENTFILE

			List<JSONObject> lsFileSync = new ArrayList<>();

			// get the list of file of source dossier need to sync
			getDossierFiles(sourceGroupId, dossierId, lsFileSync);

			pullDossierFiles(desDossier.getGroupId(), desDossier.getDossierId(), lsFileSync, sourceGroupId, dossierId);

		}

		// ResetDossier

		resetDossier(sourceGroupId, referenceUid);

	}

	private void resetDossier(long groupId, String refId) {
		try {

			_log.info("GROUPID_ID" + groupId);

			String path = "dossiers/" + refId + "/reset";
			URL url = new URL(baseUrl + path);

			HttpURLConnection conn = (HttpURLConnection) url.openConnection();

			String authString = username + ":" + password;

			String authStringEnc = new String(Base64.getEncoder().encodeToString(authString.getBytes()));
			conn.setRequestProperty("Authorization", "Basic " + authStringEnc);

			conn.setRequestMethod(HttpMethods.GET);
			conn.setDoInput(true);
			conn.setDoOutput(true);
			conn.setRequestProperty("Accept", "application/json");
			conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			conn.setRequestProperty("groupId", String.valueOf(groupId));

			if (conn.getResponseCode() != 200) {
				throw new RuntimeException("Failed : HTTP error code : " + conn.getResponseCode());
			}

			BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));

			String output;

			StringBuffer sb = new StringBuffer();

			while ((output = br.readLine()) != null) {
				sb.append(output);

			}

			System.out.println(sb.toString());

			conn.disconnect();

		} catch (MalformedURLException e) {

			e.printStackTrace();
		} catch (IOException e) {

			e.printStackTrace();

		}
	}

	private void getDossierFiles(long groupId, long dossierId, List<JSONObject> lsFileSync) {

		try {
			String path = "dossiers/" + dossierId + "/files";

			URL url = new URL(baseUrl + path);

			HttpURLConnection conn = (HttpURLConnection) url.openConnection();

			String authString = username + ":" + password;

			String authStringEnc = new String(Base64.getEncoder().encodeToString(authString.getBytes()));
			conn.setRequestProperty("Authorization", "Basic " + authStringEnc);

			conn.setRequestMethod(HttpMethods.GET);
			conn.setDoInput(true);
			conn.setDoOutput(true);
			conn.setRequestProperty("Accept", "application/json");
			conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			conn.setRequestProperty("groupId", String.valueOf(groupId));

			if (conn.getResponseCode() != 200) {
				throw new RuntimeException("Failed : HTTP error code : " + conn.getResponseCode());
			}

			BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));

			String output;

			StringBuffer sb = new StringBuffer();

			while ((output = br.readLine()) != null) {
				sb.append(output);

			}

			JSONObject jsData = JSONFactoryUtil.createJSONObject(sb.toString());

			JSONArray array = JSONFactoryUtil.createJSONArray(jsData.getString("data"));

			for (int i = 0; i < array.length(); i++) {
				JSONObject object = array.getJSONObject(i);

				if (GetterUtil.getBoolean(object.get("isNew"))) {
					lsFileSync.add(object);
				}

			}

			System.out.println(sb.toString());

			conn.disconnect();

		} catch (MalformedURLException e) {

			e.printStackTrace();
		} catch (IOException e) {

			e.printStackTrace();

		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void pullDossierFiles(long desGroupId, long dossierId, List<JSONObject> lsFileSync, long srcGroupId,
			long srcDossierId) {

		for (JSONObject ref : lsFileSync) {

			try {

				String path = "dossiers/" + srcDossierId + "/files/" + ref.getString("referenceUid");

				String requestURL = baseUrl + "dossiers/" + dossierId + "/files/";

				URL url = new URL(baseUrl + path);

				HttpURLConnection conn = (HttpURLConnection) url.openConnection();

				String authString = username + ":" + password;

				String authStringEnc = new String(Base64.getEncoder().encodeToString(authString.getBytes()));
				conn.setRequestProperty("Authorization", "Basic " + authStringEnc);

				conn.setRequestMethod(HttpMethods.GET);
				conn.setDoInput(true);
				conn.setDoOutput(true);
				conn.setRequestProperty("Accept", "application/json");
				// conn.setRequestProperty("Content-Type",
				// "multipart/form-data");
				conn.setRequestProperty("groupId", String.valueOf(srcGroupId));

				int responseCode = conn.getResponseCode();

				if (responseCode != 200) {
					throw new RuntimeException("Failed : HTTP error code : " + conn.getResponseCode());
				} else {
					String fileName = "";
					String disposition = conn.getHeaderField("Content-Disposition");
					String contentType = conn.getContentType();
					int contentLength = conn.getContentLength();

					if (disposition != null) {
						// extracts file name from header field
						int index = disposition.indexOf("filename=");
						if (index > 0) {
							fileName = disposition.substring(index + 10, disposition.length() - 1);
						}
					}

					System.out.println("Content-Type = " + contentType);
					System.out.println("Content-Disposition = " + disposition);
					System.out.println("Content-Length = " + contentLength);
					System.out.println("fileName = " + fileName);

					InputStream is = conn.getInputStream();

					File tempFile = File.createTempFile(String.valueOf(System.currentTimeMillis()),
							StringPool.PERIOD + ref.getString("fileType"));

					FileOutputStream outStream = new FileOutputStream(tempFile);

					int bytesRead = -1;
					byte[] buffer = new byte[BUFFER_SIZE];
					while ((bytesRead = is.read(buffer)) != -1) {
						outStream.write(buffer, 0, bytesRead);
					}

					outStream.close();
					is.close();

					System.out.println("File downloaded");

					pullDossierFile(requestURL, "UTF-8", desGroupId, dossierId, authStringEnc, tempFile,
							ref.getString("dossierTemplateNo"), ref.getString("dossierPartNo"),
							ref.getString("fileTemplateNo"), ref.getString("displayName"));

				}

				conn.disconnect();

			} catch (MalformedURLException e) {

				e.printStackTrace();
			} catch (IOException e) {

				e.printStackTrace();

			}

		}

	}

	private void pullDossierFile(String requestURL, String charset, long desGroupId, long dossierId,
			String authStringEnc, File file, String dossierTemplateNo, String dossierPartNo, String fileTemplateNo,
			String displayName) {

		try {
			MultipartUtility multipart = new MultipartUtility(requestURL, charset, desGroupId, authStringEnc);

			multipart.addFilePart("file", file);
			multipart.addFormField("referenceUid", PortalUUIDUtil.generate());
			multipart.addFormField("dossierTemplateNo", dossierTemplateNo);
			multipart.addFormField("dossierPartNo", dossierPartNo);
			multipart.addFormField("fileTemplateNo", fileTemplateNo);
			multipart.addFormField("displayName", displayName);

			List<String> response = multipart.finish();

			System.out.println("SERVER REPLIED:");

			for (String line : response) {
				System.out.println(line);
			}

		} catch (Exception e) {
			_log.error(e);
		}

	}

	@Activate
	@Modified
	protected void activate() {
		schedulerEntryImpl.setTrigger(
				TriggerFactoryUtil.createTrigger(getEventListenerClass(), getEventListenerClass(), 1, TimeUnit.MINUTE));
		_schedulerEngineHelper.register(this, schedulerEntryImpl, DestinationNames.SCHEDULER_DISPATCH);
	}

	@Deactivate
	protected void deactivate() {
		_schedulerEngineHelper.unregister(this);
	}

	@Reference(target = ModuleServiceLifecycle.PORTAL_INITIALIZED, unbind = "-")
	protected void setModuleServiceLifecycle(ModuleServiceLifecycle moduleServiceLifecycle) {
	}

	@Reference(unbind = "-")
	protected void setSchedulerEngineHelper(SchedulerEngineHelper schedulerEngineHelper) {

		_schedulerEngineHelper = schedulerEngineHelper;
	}

	@Reference(unbind = "-")
	protected void setTriggerFactory(TriggerFactory triggerFactory) {
	}

	private SchedulerEngineHelper _schedulerEngineHelper;

	private Log _log = LogFactoryUtil.getLog(DossierPullScheduler.class);
}
