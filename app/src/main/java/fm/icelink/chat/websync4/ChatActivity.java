package fm.icelink.chat.websync4;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.*;

import java.util.List;

import fm.icelink.Future;
import fm.icelink.IAction1;
import fm.icelink.IFunction1;
import layout.TextChatFragment;
import layout.VideoChatFragment;

public class ChatActivity extends AppCompatActivity implements VideoChatFragment.OnVideoReadyListener,
                                                                TextChatFragment.OnTextReadyListener{

    private static boolean localMediaStarted = false;
    private boolean videoReady = false;
    private boolean textReady = false;
    private App app;
    private ViewPager viewPager;
    private TabLayout tabLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        app = App.getInstance(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        viewPager = (ViewPager) findViewById(R.id.pager);
        final PagerAdapter adapter = new PagerAdapter(getSupportFragmentManager(), this);
        viewPager.setAdapter(adapter);

        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                if (position == PagerAdapter.TextTabIndex) {
                    UpdateBadge(false);
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });

        tabLayout = (TabLayout) findViewById(R.id.tab_layout);
        tabLayout.setupWithViewPager(viewPager);

        // Iterate over all tabs and set the custom view
        for (int i = 0; i < tabLayout.getTabCount(); i++) {
            TabLayout.Tab tab = tabLayout.getTabAt(i);
            tab.setCustomView(adapter.getTabView(i));
        }
    }

    public void onNewMessage()
    {
        int i = viewPager.getCurrentItem();

        if (i == PagerAdapter.VideoTabIndex) {
            UpdateBadge(true);
        }
    }

    public void onVideoReady()
    {
        videoReady = true;

        if (videoReady && textReady)
        {
            start();
        }
    }

    public void onTextReady()
    {
        textReady = true;

        if (videoReady && textReady)
        {
            start();
        }
    }

    public void onBackPressed() {

        Log.w("onBackPressed()", "Back Press invoked. EXITING VIDEO CHAT...");

        exitCall();

    }

    private void start() {
        if (!localMediaStarted) {
            List<Fragment> fragments = getSupportFragmentManager().getFragments();
            final VideoChatFragment videoChatFragment = (VideoChatFragment)(fragments.get(0) instanceof VideoChatFragment ? fragments.get(0) : fragments.get(1));
            final TextChatFragment textChatFragment = (TextChatFragment)(fragments.get(0) instanceof TextChatFragment ? fragments.get(0) : fragments.get(1));

            app.startLocalMedia(videoChatFragment).then(new IFunction1<fm.icelink.LocalMedia, Future<fm.icelink.LocalMedia>>() {
                @Override
                public Future<fm.icelink.LocalMedia> invoke(fm.icelink.LocalMedia o) {

                    return app.joinAsync(videoChatFragment, textChatFragment);
                }
            }, new fm.icelink.IAction1<Exception>() {
                @Override
                public void invoke(Exception e) {
                    fm.icelink.Log.error("Could not start local media", e);
                    alert(e.getMessage());
                }
            }).fail(new IAction1<Exception>() {
                @Override
                public void invoke(Exception e) {
                    fm.icelink.Log.error("Could not join conference.", e);
                    alert(e.getMessage());
                }
            });
        }
        localMediaStarted = true;
    }

    public void exitCall()
    {
        Log.w("END VID CALL","------------------\n -------- activity: CALL KILLED 2  --------\n------------");

        if (localMediaStarted) {
            app.leaveAsync().then(new IFunction1<Object, Future<fm.icelink.LocalMedia>>() {
                public Future<fm.icelink.LocalMedia> invoke(Object o) {
                    return app.stopLocalMedia();
                }
            }, new IAction1<Exception>() {
                @Override
                public void invoke(Exception e) {
                    Log.e("Stop Pressed","Could not leave conference :"+e);
                    alert(e.getMessage());
                }
            }).then(new IAction1<fm.icelink.LocalMedia>() {
                @Override
                public void invoke(fm.icelink.LocalMedia o) {
                    //finish();
                    exit();
                }
            }).fail(new IAction1<Exception>() {
                @Override
                public void invoke(Exception e) {
                    Log.e("","Could not stop local media: "+e);
                    alert(e.getMessage());
                }
            });
        } else {
            exit();
            //finish();
        }
        localMediaStarted = false;
    }

    private void exit()
    {
        Log.w("END VID CALL","------------------\n -------- activity: CALL KILLED 3  --------\n------------");

        Intent intentMessage = new Intent();
        intentMessage.putExtra("isReturning",true);
        setResult(Activity.RESULT_OK,intentMessage);

        finish();
    }


    public void alert(String format, Object... args) {
        final String text = String.format(format, args);
        final Activity activity = this;
        activity.runOnUiThread(new Runnable() {
            public void run() {
                AlertDialog.Builder alert = new AlertDialog.Builder(activity);
                alert.setMessage(text);
                alert.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });
                alert.show();
            }
        });
    }

    private void UpdateBadge(boolean visible) {
        View view = tabLayout.getTabAt(PagerAdapter.TextTabIndex).getCustomView();
        TextView textView = (TextView) view.findViewById(R.id.badge);
        textView.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);

        if (visible) {
            int newNumber = Integer.parseInt(textView.getText().toString()) + 1;
            textView.setText(Integer.toString(newNumber));
        } else {
            textView.setText("0");
        }
    }
}