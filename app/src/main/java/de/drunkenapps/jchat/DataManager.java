package de.drunkenapps.jchat;

import android.app.Notification;
import android.content.Context;
import android.util.Log;
import android.widget.ArrayAdapter;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

/**
 * @author max
 * @date 1/6/18.
 */

class DataManager {

    private static DataManager instance = null;

    private ArrayList<Group> groups;
    private ArrayList<Message> messages;
    private Context context;
    private ArrayList<AdapterForChats> chatAdapters;
    private ArrayList<AdapterForGroups> groupAdapters;

    private FirebaseDatabase database = FirebaseDatabase.getInstance();
    private FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
    private DatabaseReference rootNode = database.getReference();
    private DatabaseReference userRootNode = database.getReference().child("users").child(user.getUid());


    private DataManager(final Context context){
        this.context = context;

        messages = new ArrayList<>();
        groups = new ArrayList<>();
        chatAdapters = new ArrayList<>();
        groupAdapters = new ArrayList<>();

//        userRootNode.child("messages").addChildEventListener(new ChildEventListener() {
//            @Override
//            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
//                messages.add(0, dataSnapshot.getValue(Message.class));
//                for (MyAdapter listadapter :
//                        chatAdapters) {
//                    listadapter.notifyDataSetChanged();
//                }
//            }
//
//            @Override
//            public void onChildChanged(DataSnapshot dataSnapshot, String s) {
//
//            }
//
//            @Override
//            public void onChildRemoved(DataSnapshot dataSnapshot) {
//
//            }
//
//            @Override
//            public void onChildMoved(DataSnapshot dataSnapshot, String s) {
//
//            }
//
//            @Override
//            public void onCancelled(DatabaseError databaseError) {
//
//            }
//        });



        userRootNode.child("groups").addChildEventListener(new MyChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                final String groupId = dataSnapshot.getKey();

                final DatabaseReference referenceToGroup = rootNode.child("groups").child(groupId);

                Log.d("test", "groupId = " + groupId);

                final String[] groupName = {""};

                referenceToGroup.child("info").addChildEventListener(new MyChildEventListener() {
                    @Override
                    public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                        if (dataSnapshot.getKey().equals("name")) {

                            groupName[0] = (String) dataSnapshot.getValue();

                            groups.add(
                                    new Group(
                                            referenceToGroup,
                                            groupName[0],
                                            groupId
                                    )
                            );

                            Log.d("test", "groupName = " + groupName[0]);

                            final Group group = groups.get(groups.size()-1);

                            group.getDatabaseReference().child("messages").addChildEventListener(new MyChildEventListener() {
                                @Override
                                public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                                    group.addMessage(dataSnapshot.getValue(Message.class));
                                    for (ArrayAdapter chatAdapter :
                                            chatAdapters) {
                                        chatAdapter.notifyDataSetChanged();
                                    }
                                    new Notification.Builder(context)
                                            .setContentTitle("title")
                                            .setContentText("text")
                                            .setSmallIcon(R.mipmap.icon)
                                            .build();
                                }
                            });
                        }
                    }
                });
            }
        });
    }

    static DataManager getInstance(Context context) {
        if (instance == null){
            instance = new DataManager(context);
        }
        return instance;
    }

    static void dropInstance() {
        instance = null;
    }

    void pushMessage(String groupId, Message message){
        DatabaseReference newMessage = rootNode.child("groups").child(groupId).child("messages").push();
        message.setMid(newMessage.getKey());
        newMessage.setValue(message);
    }

    /**
     *
     * @param groupId is a string, representing the group id the user is trying to join
     * @return status code number:
     *      0 is representing success
     *      1 is representing non existing group
     *      2 is representing already in group
     *      3 is representing private group
     */
    int joinGroup(String groupId){
        final int[] returnResult = {0};

        rootNode.child("groups").child(groupId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if ( dataSnapshot.getValue() == null ) {
                    returnResult[0] = 1;
                    return;
                }

                if ( dataSnapshot.child("members").hasChild(user.getUid())){
                    returnResult[0] = 2;
                    return;
                }

                if ( dataSnapshot.hasChild("policy")
                                && ( (String) dataSnapshot.child("policy").getValue() ).equals("private") ){
                    returnResult[0] = 3;
                    return;
                }

                //todo: think about more returns
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

        if (returnResult[0] == 0){
            userRootNode.child("groups").child(groupId).setValue(1);
            rootNode.child("groups").child(groupId).child("members").child(user.getUid()).setValue(1);
            Log.d("joinGroup", groupId );
        }

        Log.d("joinGroup", Integer.toString(returnResult[0]) );

        return returnResult[0];
    }

    String createGroup(String name, String userId, String policy){
        DatabaseReference newGroupId = rootNode.child("groups").push();

        newGroupId.child("members").child(userId).setValue(1);
        newGroupId.child("policy").setValue(policy);
        newGroupId.child("info").child("name").setValue(name);

        userRootNode.child("groups").child(newGroupId.getKey()).setValue(1);

        Log.d("test", "created group: " + newGroupId.getKey() );

        return newGroupId.toString();
    }

    AdapterForChats getChatAdapter(String groupId){
//        Object t = new Message();//upcast
        ArrayList<Message> msg = new ArrayList<>();

        for (Group group : groups) {
            if (group.getGroupId().equals(groupId)) {
                msg = group.getMessages();
                break;
            }
        }

        AdapterForChats adapterForChats = new AdapterForChats(context, R.layout.list_entry, msg);
        chatAdapters.add(adapterForChats);
        return adapterForChats;
    }

    AdapterForGroups getGroupAdapter(){
//        Object t = new Message();//upcast
        ArrayList<Group> grp = groups;

        AdapterForGroups adapterForGroups = new AdapterForGroups(context, R.layout.list_entry, grp);
        groupAdapters.add(adapterForGroups);
        return adapterForGroups;
    }

    ArrayList<Group> getGroups() {
        return groups;
    }

    void updateAll() {
        for (AdapterForGroups adapterForGroups: groupAdapters){
            adapterForGroups.notifyDataSetChanged();
        }

        for (AdapterForChats adapterForChats: chatAdapters){
            adapterForChats.notifyDataSetChanged();
        }
    }
}
