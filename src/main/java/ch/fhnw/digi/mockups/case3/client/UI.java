package ch.fhnw.digi.mockups.case3.client;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.annotation.PostConstruct;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import ch.fhnw.digi.mockups.case3.JobAssignmentMessage;
import ch.fhnw.digi.mockups.case3.JobMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;



@SuppressWarnings("serial")
@Component
public class UI extends JFrame {

	@Autowired
	private MessageSender messageSender;

	private JList<JobMessage> m_list_jobs;
	private DefaultListModel<JobMessage> m_list_jobs_model;

	private JList<String> m_list_assignments;
	private DefaultListModel<String> m_list_assignments_model;

	private JButton m_btn_requestJob;

	@PostConstruct
	void init() {

		m_list_jobs_model = new DefaultListModel<JobMessage>();
		m_list_jobs = new JList<JobMessage>(m_list_jobs_model);

		m_list_assignments_model = new DefaultListModel<String>();
		m_list_assignments = new JList<String>(m_list_assignments_model);

		m_btn_requestJob = new JButton("Selektierten Job anfordern");
		m_btn_requestJob.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				acceptSelectedJob();
			}
		});

		JPanel rootPanel = new JPanel(new BorderLayout());
		JPanel listsPanel = new JPanel();
		listsPanel.setLayout(new BoxLayout(listsPanel, BoxLayout.X_AXIS));
		JPanel jobsPanel = new JPanel(new BorderLayout());
		JPanel assignmentsPanel = new JPanel(new BorderLayout());
		
		
		jobsPanel.add(new JLabel("offene Jobs:"),BorderLayout.NORTH);
		
		
		jobsPanel.add(new JScrollPane(m_list_jobs), BorderLayout.CENTER);
		jobsPanel.setMinimumSize(new Dimension(200,200));
		assignmentsPanel.add(new JLabel("zugewiesene Jobs:"),BorderLayout.NORTH);
		assignmentsPanel.add(new JScrollPane(m_list_assignments), BorderLayout.CENTER);
		assignmentsPanel.setMinimumSize(new Dimension(200,200));
		listsPanel.add(jobsPanel);
		listsPanel.add(assignmentsPanel);
		rootPanel.add(m_btn_requestJob, BorderLayout.SOUTH);
		rootPanel.add(listsPanel,BorderLayout.CENTER);

		
		
		getContentPane().add(rootPanel);

		setSize(800, 600);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setVisible(true);
	}

	protected void acceptSelectedJob() {
		JobMessage m = m_list_jobs.getSelectedValue();
		if (m == null)
			return;

		messageSender.requestJobFromDispo(m);

	}

	public void addJobToList(JobMessage j) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				synchronized (m_list_jobs_model) {
					m_list_jobs_model.add(0, j);
				}
			}

		});
	}

	public void assignJob(JobAssignmentMessage c) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				if (c.isAssigned()) {
					// Zugewiesen: Job aus offener Liste entfernen und in zugewiesene Liste verschieben
					synchronized (m_list_jobs_model) {
						for (int x = 0; x < m_list_jobs_model.getSize(); ++x) {
							if (m_list_jobs_model.get(x).getJobId().equals(c.getJobId())) {
								m_list_jobs_model.remove(x);
								break;
							}
						}
					}
					synchronized (m_list_assignments_model) {
						m_list_assignments_model.add(0,
								"Job " + c.getJobId() + " ZUGEWIESEN an \"" + c.getClientId() + "\"");
					}
				} else {
					// Abgelehnt: Job bleibt in offener Liste, nur Hinweis anzeigen
					synchronized (m_list_assignments_model) {
						m_list_assignments_model.add(0,
								"Job " + c.getJobId() + " ABGELEHNT für \"" + c.getClientId() + "\"");
					}
				}
			}
		});
	}
}
