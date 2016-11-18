package layout;

import android.support.v4.app.Fragment;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.method.ScrollingMovementMethod;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import fm.icelink.chat.websync4.App;
import fm.icelink.chat.websync4.R;

public class TextChatFragment extends Fragment implements App.OnReceivedTextListener {
    private App app;
    private OnTextReadyListener listener;
    private TextView textView;
    private Button submitButton;
    private EditText editText;

    int peers = 0;

    public TextChatFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        app = App.getInstance(null);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_text_chat, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        textView = (TextView)view.findViewById(R.id.log);
        editText = (EditText)view.findViewById(R.id.text);
        textView.setMovementMethod(new ScrollingMovementMethod());
        submitButton = (Button)view.findViewById(R.id.send);

        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String msg = editText.getText().toString().trim();
                editText.setText("");

                if (msg.length() > 0) {
                    onReceivedText("Me", msg);
                    app.writeLine(msg);
                }
            }
        });

        listener.onTextReady();
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    public void onReceivedText(final String name, String message)
    {
        final String concatMessage = name + ": " + message + "\n";
        writeLine(new SpannableString(concatMessage)
        {{
            setSpan(new StyleSpan(Typeface.BOLD), 0, name.length(), 0);
        }});
    }

    public void onPeerJoined(String name)
    {
        peers++;

        final String message = name + " has joined.\n";
        writeLine(new SpannableString(message)
        {{
            setSpan(new StyleSpan(Typeface.BOLD), 0, message.length(), 0);
        }});

        updateButton();
    }

    public void onPeerLeft(String name)
    {
        peers--;

        final String message = name + " has left.\n";
        writeLine(new SpannableString(message)
        {{
            setSpan(new StyleSpan(Typeface.BOLD), 0, message.length(), 0);
        }});

        updateButton();
    }

    private void updateButton() {
        if (peers > 0) {
            if (!submitButton.isEnabled()) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        submitButton.setEnabled(true);
                    }
                });
            }
        } else {
            if (submitButton.isEnabled()) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        submitButton.setEnabled(false);
                    }
                });
            }
        }
    }

    private void writeLine(final SpannableString str)
    {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textView.append(str);
                listener.onNewMessage();
            }
        });
    }

    public interface OnTextReadyListener {
        void onTextReady();
        void onNewMessage();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnTextReadyListener) {
            listener = (OnTextReadyListener) context;
        } else {
            throw new ClassCastException(context.toString()
                    + " must implement TextChatFragment.OnTextReadyListener");
        }
    }
}
