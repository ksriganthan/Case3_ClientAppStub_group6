package ch.fhnw.digi.mockups.case3;

import java.time.LocalDateTime;

public class JobMessage {

	private String jobId;
	private String description;
	private String region;
	private String jobType; // "repair" oder "maintenance"
	private String  scheduledDateTime;

	public JobMessage() {
	}

	public JobMessage(String jobId, String description, String region, String jobType, String scheduledDateTime) {
		this.jobId = jobId;
		this.description = description;
		this.region = region;
		this.jobType = jobType;
		this.scheduledDateTime = scheduledDateTime;
	}

	public String getScheduledDateTime() {
		return scheduledDateTime;
	}
	public void setScheduledDateTime(String scheduledDateTime) {
		this.scheduledDateTime = scheduledDateTime;
	}

	public String getJobId() {
		return jobId;
	}

	public void setJobId(String jobId) {
		this.jobId = jobId;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getRegion() {
		return region;
	}

	public void setRegion(String region) {
		this.region = region;
	}

	public String getJobType() {
		return jobType;
	}

	public void setJobType(String jobType) {
		this.jobType = jobType;
	}

	@Override
	public String toString() {
		return "Job{id='" + jobId + "', typ='" + jobType + "', region='" + region + "', beschreibung='" + description + "'}";
	}
}

