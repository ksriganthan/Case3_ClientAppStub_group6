package ch.fhnw.digi.mockups.case3;

public class JobRequestMessage {

	private String jobId;
	private String clientId;

	public JobRequestMessage() {
	}

	public JobRequestMessage(String jobId, String clientId) {
		this.jobId = jobId;
		this.clientId = clientId;
	}

	public String getJobId() {
		return jobId;
	}

	public void setJobId(String jobId) {
		this.jobId = jobId;
	}

	public String getClientId() {
		return clientId;
	}

	public void setClientId(String clientId) {
		this.clientId = clientId;
	}
}

