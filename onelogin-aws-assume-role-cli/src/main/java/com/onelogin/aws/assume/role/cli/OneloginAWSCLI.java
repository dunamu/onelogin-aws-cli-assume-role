package com.onelogin.aws.assume.role.cli;

import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.profile.ProfilesConfigFileWriter;
import com.amazonaws.auth.profile.internal.Profile;
import com.amazonaws.auth.profile.internal.ProfileKeyConstants;
import com.amazonaws.profile.path.AwsProfileFileLocationProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.AWSSecurityTokenServiceException;
import com.amazonaws.services.securitytoken.model.AssumeRoleWithSAMLRequest;
import com.amazonaws.services.securitytoken.model.AssumeRoleWithSAMLResult;
import com.amazonaws.services.securitytoken.model.AssumedRoleUser;
import com.amazonaws.services.securitytoken.model.Credentials;
import com.onelogin.saml2.authn.SamlResponse;
import com.onelogin.saml2.http.HttpRequest;
import com.onelogin.sdk.conn.Client;
import com.onelogin.sdk.model.Device;
import com.onelogin.sdk.model.MFA;
import com.onelogin.sdk.model.SAMLEndpointResponse;

public class OneloginAWSCLI {

	private static int time = 45;
	private static int loop = 1;
	private static String profileName = null;
	private static File file = null;
	private static String oneloginUsernameOrEmail = null;
	private static String oneloginPassword = null;
	private static String appId = null;
	private static String oneloginDomain = null;
	private static String awsRegion = null;
	private static String awsAccountId = null;
	private static String awsRoleName = null;
	private static int duration = 900;
	private static String oneloginClientID = null;
	private static String oneloginClientSecret = null;
	private static String oneloginRegion = "us";
	private static Boolean useOneloginPush = false;
	private static String oneloginDeviceNumber = null;

	public static Boolean commandParser(final String[] commandLineArguments) {
		final CommandLineParser cmd = new DefaultParser();
		final Options options = buildOptions();
		CommandLine commandLine;
		try {
			commandLine = cmd.parse(options, commandLineArguments);
			String value;

			if (commandLine.hasOption("help")) {
				HelpFormatter hf = new HelpFormatter();
				hf.printHelp("onelogin-aws-cli.jar [options]", options);
				System.out.println("");
				return false;
			}

			if (commandLine.hasOption("time")) {
				value = commandLine.getOptionValue("time");
				if (value != null && !value.isEmpty()) {
					time = Integer.parseInt(value);
				}
				if (time < 15 ) {
					time = 15;
				}
				if (time > 60 ) {
					time = 60;
				}
			}
			if (commandLine.hasOption("loop")) {
				value = commandLine.getOptionValue("loop");
				if (value != null && !value.isEmpty()) {
					loop = Integer.parseInt(value);
				}
			}
			if (commandLine.hasOption("profile")) {
				value = commandLine.getOptionValue("profile");
				if (value != null && !value.isEmpty()) {
					profileName = value;
				} else {
					profileName = "default";
				}
			}
			if (commandLine.hasOption("file")) {
				value = commandLine.getOptionValue("file");
				if (value != null && !value.isEmpty()) {
					file = new File(value);
				}
			}

			if (commandLine.hasOption("username")) {
				value = commandLine.getOptionValue("username");
				if (value != null && !value.isEmpty()) {
					oneloginUsernameOrEmail = value;
				}
			}

			if (commandLine.hasOption("subdomain")) {
				value = commandLine.getOptionValue("subdomain");
				if (value != null && !value.isEmpty()) {
					oneloginDomain = value;
				}
			}

			if (commandLine.hasOption("appid")) {
				value = commandLine.getOptionValue("appid");
				if (value != null && !value.isEmpty()) {
					appId = value;
				}
			}

			if (commandLine.hasOption("region")) {
				value = commandLine.getOptionValue("region");
				if (value != null && !value.isEmpty()) {
					awsRegion = value;
				}
			}

			if (commandLine.hasOption("password")) {
				value = commandLine.getOptionValue("password");
				if (value != null && !value.isEmpty()) {
					oneloginPassword = value;
				}
			}

			if (commandLine.hasOption("aws-account-id")) {
				value = commandLine.getOptionValue("aws-account-id");
				if (value != null && !value.isEmpty()) {
					awsAccountId = value;
				}
			}

			if (commandLine.hasOption("aws-role-name")) {
				value = commandLine.getOptionValue("aws-role-name");
				if (value != null && !value.isEmpty()) {
					awsRoleName = value;
				}
			}

			if (commandLine.hasOption("duration")) {
				value = commandLine.getOptionValue("duration");
				if (value != null && !value.isEmpty()) {
					duration = Integer.parseInt(value);
				}
				if (duration < 900) {
					duration = 900;
				} else if (duration > 43200) {
					duration = 900;
				}
			} else {
				duration = 900;
			}

			if (commandLine.hasOption("onelogin-client-id")) {
				value = commandLine.getOptionValue("onelogin-client-id");
				if (value != null && !value.isEmpty()) {
					oneloginClientID = value;
				}
			}

			if (commandLine.hasOption("onelogin-client-secret")) {
				value = commandLine.getOptionValue("onelogin-client-secret");
				if (value != null && !value.isEmpty()) {
					oneloginClientSecret = value;
				}
			}

			if (commandLine.hasOption("onelogin-region")) {
				value = commandLine.getOptionValue("onelogin-region");
				if (value != null && !value.isEmpty()) {
					oneloginRegion = value;
				}
			}

			if (commandLine.hasOption("use-onelogin-push")) {
				useOneloginPush = true;
			}

			if (commandLine.hasOption("device-number")) {
				value = commandLine.getOptionValue("device-number");
				if (value != null && !value.isEmpty()) {
					oneloginDeviceNumber = value;
				}
			}

			// VALIDATIONS

			if (((awsAccountId != null && !awsAccountId.isEmpty()) && (awsRoleName == null || awsRoleName.isEmpty())) || ((awsRoleName != null && !awsRoleName.isEmpty()) && (awsAccountId == null || awsAccountId.isEmpty()))) {
				System.err.println("--aws-account-id and --aws-role-name need to be set together");
				return false;
			}

			if (((oneloginClientID != null && !oneloginClientID.isEmpty()) && (oneloginClientSecret == null || oneloginClientSecret.isEmpty())) || ((oneloginClientSecret != null && !oneloginClientSecret.isEmpty()) && (oneloginClientID == null || oneloginClientID.isEmpty()))) {
				System.err.println("--onelogin-client-id and --onelogin-client-secret need to be set together");
				return false;
			}
			return true;
		}
		catch (ParseException parseException) {
			System.err.println("Encountered exception while parsing" + parseException.getMessage());
			return false;
		}
	}

	public static Options buildOptions() {
		final Options options = new Options();
		options.addOption("h", "help", false, "Show the help guide");
		options.addOption("t", "time", true, "Sleep time between iterations, in minutes  [15-60 min]");
		options.addOption("l", "loop", true, "Number of iterations");
		options.addOption("p", "profile", true, "Save temporary AWS credentials using that profile name");
		options.addOption("f", "file", true, "Set a custom path to save the AWS credentials. (if not used, default AWS path is used)");
		options.addOption("r", "region", true, "Set the AWS region.");
		options.addOption("a", "appid", true, "Set AWS App ID.");
		options.addOption("d", "subdomain", true, "OneLogin Instance Sub Domain.");
		options.addOption("u", "username", true, "OneLogin username.");
		options.addOption(null, "password", true, "OneLogin password.");
		options.addOption(null, "aws-account-id", true, "AWS Account ID.");
		options.addOption(null, "aws-role-name", true, "AWS Role Name.");
		options.addOption("z", "duration", true, "Desired AWS Credential Duration");
		options.addOption(null, "onelogin-client-id", true, "A valid OneLogin API client_id");
		options.addOption(null, "onelogin-client-secret", true, "A valid OneLogin API client_secret");
		options.addOption(null, "onelogin-region", true, "Onelogin region. us or eu  (Default value: us)");
		options.addOption(null, "use-onelogin-push", false, "Use push notification method for Onelogin Protect.");
		options.addOption(null, "device-number", true, "OneLogin 2fa device number.");

		return options;
	}

	public static void main(String[] commandLineArguments) throws Exception {

		System.out.println("\nOneLogin AWS Assume Role Tool (Dunamu customized ver.)\n");

		if(!commandParser(commandLineArguments)){
			return;
		}

		// OneLogin Java SDK Client
		Client olClient;
		if ((oneloginClientID == null || oneloginClientID.isEmpty()) && (oneloginClientSecret == null || oneloginClientSecret.isEmpty())) {
			olClient = new Client();
		} else {
			olClient = new Client(oneloginClientID, oneloginClientSecret, oneloginRegion);
		}
		String ip = olClient.getIP();
		olClient.getAccessToken();
		Scanner scanner = new Scanner(System.in);
		int currentDuration = duration;
		try {
			String samlResponse;

			Map<String, String> mfaVerifyInfo = null;
			Map<String, Object> result;

			String roleArn = null;
			String principalArn = null;
			String defaultAWSRegion = Regions.DEFAULT_REGION.getName();

			for (int i = 0; i < loop; i++) {
				if (i == 0) {
					// Capture OneLogin Account Details
					System.out.print("OneLogin Username: ");
					if (oneloginUsernameOrEmail == null) {
						oneloginUsernameOrEmail = scanner.next();
					} else{
						System.out.println(oneloginUsernameOrEmail);
					}

					if (oneloginPassword == null) {
						System.out.print("OneLogin Password: ");
						try {
							oneloginPassword = String.valueOf(System.console().readPassword());
						} catch (Exception e) {
							oneloginPassword = scanner.next();
						}
					}
					System.out.print("AWS App ID: ");
					if (appId == null) {
						appId = scanner.next();
					} else {
							System.out.println(appId);
					}

					System.out.print("Onelogin Instance Sub Domain: ");
					if (oneloginDomain == null) {
						oneloginDomain = scanner.next();
					} else {
						System.out.println(oneloginDomain);
					}
				} else {
					TimeUnit.MINUTES.sleep(time);
				}

				result = getSamlResponse(olClient, scanner, oneloginUsernameOrEmail, oneloginPassword, appId,
						oneloginDomain, mfaVerifyInfo, ip);
				mfaVerifyInfo = (Map<String, String>) result.get("mfaVerifyInfo");
				samlResponse = (String) result.get("samlResponse");

				if (i == 0) {
					HttpRequest simulatedRequest = new HttpRequest("http://example.com");
					simulatedRequest = simulatedRequest.addParameter("SAMLResponse", samlResponse);
					SamlResponse samlResponseObj = new SamlResponse(null, simulatedRequest);
					HashMap<String, List<String>> attributes = samlResponseObj.getAttributes();
					if (!attributes.containsKey("https://aws.amazon.com/SAML/Attributes/Role")) {
						System.out.print("SAMLResponse from Identity Provider does not contain AWS Role info");
						System.exit(0);
					} else {
						String selectedRole = "";
						List<String> roleDataList = attributes.get("https://aws.amazon.com/SAML/Attributes/Role");
						List<String> roleData = null;
						if (awsAccountId != null) {
							roleData = new ArrayList();
							for (int j = 0; j < roleDataList.size(); j++) {
								String[] roleInfo = roleDataList.get(j).split(":");
								String accountId = roleInfo[4];
								if (accountId.equals(awsAccountId)) {
									roleData.add(roleDataList.get(j));
								}
							}
						} else {
    						roleData = new ArrayList(roleDataList);
						}

						if (roleData.size() == 1 && !roleData.get(0).isEmpty()) {
							String[] roleInfo = roleData.get(0).split(":");
							String accountId = roleInfo[4];
							String roleName = roleInfo[5].replace("role/", "");
							System.out.println("Role selected: " + roleName + " (Account " + accountId + ")");
							selectedRole = roleData.get(0);
						} else if (roleData.size() > 1) {
							System.out.println("\nAvailable AWS Roles");
							System.out.println("-----------------------------------------------------------------------");
							Map<String, Map<String, Integer>> rolesByApp = new HashMap<String,Map<String, Integer>>();
							Map<String, Integer> val = null;
							for (int j = 0; j < roleData.size(); j++) {
								String[] roleInfo = roleData.get(j).split(",")[0].split(":");
								String accountId = roleInfo[4];
								String roleName = roleInfo[5].replace("role/", "");
								System.out.println(" " + j + " | " + roleName + " (Account " + accountId + ")");
								if (rolesByApp.containsKey(accountId)) {
									rolesByApp.get(accountId).put(roleName,j);
								} else {
									val = new HashMap<String, Integer>();
									val.put(roleName, j);
									rolesByApp.put(accountId, val);
								}
							}

							Integer roleSelection = null;
							if (awsAccountId != null && awsRoleName != null && rolesByApp.containsKey(awsAccountId) && rolesByApp.get(awsAccountId).containsKey(awsRoleName)) {
								roleSelection = rolesByApp.get(awsAccountId).get(awsRoleName);
							}

							if (roleSelection == null) {
								if (awsAccountId != null && !awsAccountId.isEmpty() && awsRoleName != null && !awsRoleName.isEmpty()) {
									System.out.println("SAMLResponse from Identity Provider does not contain available AWS Role: " + awsAccountId + " for AWS Account: " + awsRoleName);
								}
								System.out.println("-----------------------------------------------------------------------");
								System.out.print("Select the desired Role [0-" + (roleData.size() - 1) + "]: ");
								roleSelection = getSelection(scanner, roleData.size());
							}
							selectedRole = roleData.get(roleSelection);
						} else {
							System.out.print("SAMLResponse from Identity Provider does not contain available AWS Role for this user");
							System.exit(0);
						}

						if (!selectedRole.isEmpty()) {
							String[] selectedRoleData = selectedRole.split(",");
							roleArn = selectedRoleData[0];
							principalArn = selectedRoleData[1];
						}
					}
				}

				if (i == 0) {
					// AWS REGION
					if (awsRegion == null) {
						System.out.print("AWS Region (" + defaultAWSRegion + "): ");
						awsRegion = scanner.next();
						if (awsRegion.isEmpty() || awsRegion.equals("-")) {
							awsRegion = defaultAWSRegion;
						}
					} else {
						System.out.print("AWS Region: " + awsRegion);
					}
				}

				BasicAWSCredentials awsCredentials = new BasicAWSCredentials("", "");

				AWSSecurityTokenServiceClientBuilder stsBuilder = AWSSecurityTokenServiceClientBuilder.standard();

				AWSSecurityTokenService stsClient = stsBuilder.withRegion(awsRegion)
						.withCredentials(new AWSStaticCredentialsProvider(awsCredentials)).build();

				AssumeRoleWithSAMLRequest assumeRoleWithSAMLRequest = null;
				AssumeRoleWithSAMLResult assumeRoleWithSAMLResult = null;
				try {
					assumeRoleWithSAMLRequest = new AssumeRoleWithSAMLRequest()
							.withPrincipalArn(principalArn).withRoleArn(roleArn).withSAMLAssertion(samlResponse).withDurationSeconds(currentDuration);
					assumeRoleWithSAMLResult = stsClient
							.assumeRoleWithSAML(assumeRoleWithSAMLRequest);
				} catch (AWSSecurityTokenServiceException e) {
					if (e.getErrorMessage().contains("'durationSeconds' failed to satisfy constraint") || e.getErrorMessage().contains("DurationSeconds exceeds")) {
						System.out.print("Introduce a new value, to be used on this Role, for DurationSeconds between 900 and 43200. Previously was "+ currentDuration + ": ");
						currentDuration = getDuration(scanner);

						assumeRoleWithSAMLRequest = new AssumeRoleWithSAMLRequest()
								.withPrincipalArn(principalArn).withRoleArn(roleArn).withSAMLAssertion(samlResponse).withDurationSeconds(currentDuration);
						assumeRoleWithSAMLResult = stsClient
								.assumeRoleWithSAML(assumeRoleWithSAMLRequest);
					} else {
						throw e;
					}
				}

				Credentials stsCredentials = assumeRoleWithSAMLResult.getCredentials();
				AssumedRoleUser assumedRoleUser = assumeRoleWithSAMLResult.getAssumedRoleUser();

				if (profileName == null && file == null) {
					String action = "export";
					if (System.getProperty("os.name").toLowerCase().contains("win")) {
						action = "set";
					}
					System.out.println("\n-----------------------------------------------------------------------\n");
					System.out.println("Success!\n");
					System.out.println("Assumed Role User: " + assumedRoleUser.getArn() + "\n");
					System.out.println("Temporary AWS Credentials Granted via OneLogin\n ");
					System.out.println("It will expire at " + stsCredentials.getExpiration());
					System.out.println("Copy/Paste to set these as environment variables\n");
					System.out.println("-----------------------------------------------------------------------\n");

					System.out.println(action + " AWS_SESSION_TOKEN=" + stsCredentials.getSessionToken());
					System.out.println();
					System.out.println(action + " AWS_ACCESS_KEY_ID=" + stsCredentials.getAccessKeyId());
					System.out.println();
					System.out.println(action + " AWS_SECRET_ACCESS_KEY=" + stsCredentials.getSecretAccessKey());
					System.out.println();
				} else {
					if (file == null) {
						file = AwsProfileFileLocationProvider.DEFAULT_CREDENTIALS_LOCATION_PROVIDER.getLocation();
					}
					if (profileName == null) {
						profileName = "default";
					}

					Map<String, String> properties = new HashMap<String, String>();
					properties.put(ProfileKeyConstants.AWS_ACCESS_KEY_ID, stsCredentials.getAccessKeyId());
					properties.put(ProfileKeyConstants.AWS_SECRET_ACCESS_KEY, stsCredentials.getSecretAccessKey());
					properties.put(ProfileKeyConstants.AWS_SESSION_TOKEN, stsCredentials.getSessionToken());
					properties.put(ProfileKeyConstants.REGION, awsRegion);

					ProfilesConfigFileWriter.modifyOneProfile(file, profileName, new Profile(profileName, properties, null));

					System.out.println("\n-----------------------------------------------------------------------");
					System.out.println("Success!\n");
					System.out.println("Temporary AWS Credentials Granted via OneLogin\n");
					System.out.println("Updated AWS profile '" + profileName + "' located at " + file.getAbsolutePath());
					if (loop > (i+1)) {
						System.out.println("This process will regenerate credentials " + (loop - (i+1)) + " more times.\n");
						System.out.println("Press Ctrl + C to exit");
					}
				}
			}
		} finally {
			scanner.close();
		}
	}

	public static Integer getSelection(Scanner scanner, int max)
	{
		Integer selection = Integer.valueOf(scanner.next());
		while (selection < 0 || selection >= max) {
			System.out.println("Wrong number, add a number between 0 - " + (max - 1));
			selection = Integer.valueOf(scanner.next());
		}
		return selection;
	}

	public static Map<String, Object> getSamlResponse(Client olClient, Scanner scanner, String oneloginUsernameOrEmail,
			String oneloginPassword, String appId, String oneloginDomain, Map<String, String> mfaVerifyInfo, String ip)
			throws Exception {
		String otpToken = null, stateToken;
		Device deviceSelection;
		Long deviceId;
		String deviceIdStr = null;
		Map<String, Object> result = new HashMap<String, Object>();

		SAMLEndpointResponse samlEndpointResponse = olClient.getSAMLAssertion(oneloginUsernameOrEmail, oneloginPassword,
				appId, oneloginDomain, ip);
		String status = samlEndpointResponse.getType();
		while (status.equals("pending")) {
			TimeUnit.SECONDS.sleep(30);
			samlEndpointResponse = olClient.getSAMLAssertion(oneloginUsernameOrEmail, oneloginPassword, appId,
					oneloginDomain, ip);
			status = samlEndpointResponse.getType();
		}
		String samlResponse = null;
		if (status.equals("success")) {
			if (samlEndpointResponse.getMFA() != null) {
				MFA mfa = samlEndpointResponse.getMFA();
				List<Device> devices = mfa.getDevices();

				if (mfaVerifyInfo == null) {
					System.out.println();
					System.out.println("MFA Required");
					System.out.println("Authenticate using one of these devices:");
				} else {
					deviceIdStr = mfaVerifyInfo.get("deviceId");
					if (!checkDeviceExists(devices, Long.parseLong(deviceIdStr))) {
						System.out.println();
						System.out.println("The device selected with ID " + deviceIdStr + " is not available anymore");
						System.out.println("Those are the devices available now:");
						mfaVerifyInfo = null;
					}
				}

				if (mfaVerifyInfo == null) {
					System.out.println("-----------------------------------------------------------------------");
					Device device;
					Integer deviceInput;

					if (devices.size() == 1) {
						deviceInput = 0;
					} else {
						for (int i = 0; i < devices.size(); i++) {
							device = devices.get(i);
							System.out.println(" " + i + " | " + device.getType());
						}
						System.out.println("-----------------------------------------------------------------------");
						if (oneloginDeviceNumber == null) {
							System.out.print("\nSelect the desired MFA Device [0-" + (devices.size() - 1) + "]: ");
							deviceInput = getSelection(scanner, devices.size());
						} else {
							System.out.println("\nAuto-selecting MFA Device from command line argument: " + oneloginDeviceNumber);
							deviceInput = Integer.parseInt(oneloginDeviceNumber);
							System.out.println("Auto-selected MFA Device [" + devices.get(deviceInput).getType() + "]");
						}
					}

					System.out.println();

					deviceSelection = devices.get(deviceInput);
					deviceId = deviceSelection.getID();
					deviceIdStr = deviceId.toString();

					boolean usePush = useOneloginPush;
					if (deviceSelection.getType().equals("OneLogin Protect")) {
						if (!usePush) {
							scanner.nextLine();
							System.out.print("Enter the OTP Token for OneLogin Protect or just press enter to use push notification method: ");
							otpToken = scanner.nextLine();

							if (otpToken.isEmpty()) {
								usePush = true;
								System.out.println();
							}
						}

						if (usePush) {
							System.out.println("Check your phone for the OneLogin Protect notification.");
							otpToken = null;
						}
					} else {
						System.out.print("Enter the OTP Token for " + deviceSelection.getType() + ": ");
						otpToken = scanner.next();
					}

					stateToken = mfa.getStateToken();
					mfaVerifyInfo = new HashMap<String, String>();
					mfaVerifyInfo.put("otpToken", otpToken);
					mfaVerifyInfo.put("stateToken", stateToken);
				} else {
					otpToken = mfaVerifyInfo.get("otpToken");
					stateToken = mfaVerifyInfo.get("stateToken");
				}
				result = verifyToken(olClient, scanner, appId,
						deviceIdStr, stateToken, otpToken, mfaVerifyInfo);

			} else {
				samlResponse = samlEndpointResponse.getSAMLResponse();
				result.put("samlResponse", samlResponse);
				result.put("mfaVerifyInfo", mfaVerifyInfo);
			}
		}
		return result;
	}

	public static Integer getDuration(Scanner scanner) {
		Integer answer = null;
		String value = null;
		boolean start = true;
		while (answer == null || (answer < 900 || answer > 43200)) {
			if (!start) {
				System.out.println("Wrong value, insert a value between 900 and 43200: ");
			}
			start = false;
			value = scanner.next();
			try {
				answer = Integer.valueOf(value);
			} catch (Exception e) {
				continue;
			}
		}
		return answer;
	}

	public static Map<String, Object> getSamlResponse(Client olClient, Scanner scanner, String oneloginUsernameOrEmail,
			String oneloginPassword, String appId, String oneloginDomain, Map<String, String> mfaVerifyInfo)
			throws Exception {
		return getSamlResponse(olClient, scanner, oneloginUsernameOrEmail, oneloginPassword, appId,
				oneloginDomain, mfaVerifyInfo, null);
	}

	public static Boolean checkDeviceExists(List<Device> devices, Long deviceId) {
		for (Device device : devices) {
			if (device.getID() == deviceId) {
				return true;
			}
		}
		return false;
	}

	public static Map<String, Object> verifyToken(Client olClient, Scanner scanner, String appId,
			String deviceIdStr, String stateToken, String otpToken, Map<String, String> mfaVerifyInfo) {
		Map<String, Object> result = new HashMap<String, Object>();
		try {
			SAMLEndpointResponse samlEndpointResponseAfterVerify = olClient.getSAMLAssertionVerifying(appId,
				deviceIdStr, stateToken, otpToken, null);
			mfaVerifyInfo.put("otpToken", otpToken);

			while (samlEndpointResponseAfterVerify.getType().equals("pending")) {
				samlEndpointResponseAfterVerify = olClient.getSAMLAssertionVerifying(appId,
						deviceIdStr, stateToken, otpToken, null, true);

				TimeUnit.SECONDS.sleep(1);
			}

			String samlResponse = samlEndpointResponseAfterVerify.getSAMLResponse();
			result.put("samlResponse", samlResponse);
			result.put("mfaVerifyInfo", mfaVerifyInfo);
		} catch (Exception OAuthProblemException){
			System.out.print("The OTP Token was invalid, please introduce a new one: ");
			otpToken = scanner.next();
			result = verifyToken(olClient, scanner, appId,
					deviceIdStr, stateToken, otpToken, mfaVerifyInfo);
		}
		return result;
	}

}
