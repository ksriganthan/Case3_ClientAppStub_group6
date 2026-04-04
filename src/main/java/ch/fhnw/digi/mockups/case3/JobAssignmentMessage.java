package ch.fhnw.digi.mockups.case3;

public class JobAssignmentMessage {

	private String jobId;
	private String clientId;
	private boolean assigned;

	public JobAssignmentMessage() {
	}

	public JobAssignmentMessage(String jobId, String clientId, boolean assigned) {
		this.jobId = jobId;
		this.clientId = clientId;
		this.assigned = assigned;
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

	public boolean isAssigned() {
		return assigned;
	}

	public void setAssigned(boolean assigned) {
		this.assigned = assigned;
	}
}

