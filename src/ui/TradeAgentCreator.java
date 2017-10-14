package ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.lang.reflect.Field;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import agent.ApplianceAgent;
import annotations.Adjustable;

import javax.swing.JTextField;
import javax.swing.JLabel;
import java.awt.Rectangle;

public class TradeAgentCreator extends JDialog {

	private final JPanel contentPanel = new JPanel();
	
	private Object instance;
	
	public TradeAgentCreator(Class<?> type) throws InstantiationException, IllegalAccessException {
		setBounds(new Rectangle(37, 23, 300, 400));
		this.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		setTitle("Create a "+ type.getSimpleName());
		setModalityType(ModalityType.DOCUMENT_MODAL);
		setModal(true);
		getContentPane().setLayout(new BorderLayout());
		contentPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
		getContentPane().add(contentPanel, BorderLayout.CENTER);
		
		contentPanel.setLayout(new GridLayout(5,1));
		
		/* Create the instance */
		instance = type.newInstance();
		
		for(Field f : type.getDeclaredFields()) {

			/* only get the adjustable fields */
			if(f.getAnnotationsByType(Adjustable.class)!=null) {
				Adjustable c = f.getAnnotation(Adjustable.class);
				JLabel label = new JLabel(c.label());
				contentPanel.add(label);
				JTextField input= new JTextField();
				contentPanel.add(input);
				input.setColumns(10);
			}
		}
		{
			JPanel buttonPane = new JPanel();
			buttonPane.setLayout(new FlowLayout(FlowLayout.RIGHT));
			getContentPane().add(buttonPane, BorderLayout.SOUTH);
			{
				JButton okButton = new JButton("CREATE");
				okButton.setActionCommand("Create");
				buttonPane.add(okButton);
				getRootPane().setDefaultButton(okButton);
			}
			{
				JButton cancelButton = new JButton("Cancel");
				cancelButton.setActionCommand("Cancel");
				buttonPane.add(cancelButton);
			}
		}
	}

}
