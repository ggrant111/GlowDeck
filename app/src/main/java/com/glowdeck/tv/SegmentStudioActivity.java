package com.glowdeck.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.res.Configuration;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.text.InputType;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Device-adaptive native Segment Studio. No WebView or cloud dependency. */
public final class SegmentStudioActivity extends Activity {
    private static final int BG=0xFF080E1C, CARD=0xFF14233B, ACCENT=0xFF4BB7FF, WHITE=0xFFF3F7FF;
    private static final int MUTED=0xFF9FB2CE;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final List<WledDevice> devices=new ArrayList<>();
    private final List<SegmentZone> zones=new ArrayList<>();
    private GlowDeckRepository repo;
    private SegmentPreviewManager preview;
    private LinearLayout content;
    private TextView status;
    private boolean zoneTab=false;
    private final Runnable previewTimeout=()->stopPreview("Identification timed out");
    private boolean stopped=false;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        repo=new GlowDeckRepository(this,getPreferences(MODE_PRIVATE));
        preview=new SegmentPreviewManager(this);
        zones.addAll(repo.loadZones());
        loadDevices();
        buildShell();
        refresh();
    }
    @Override protected void onResume() {
        super.onResume();
        stopped=false;
        worker.execute(()->{
            String restored=preview.recover();
            if(!"No active identification".equals(restored))ui.post(()->notifyStatus(restored));
        });
    }
    @Override protected void onPause() {
        stopped=true;ui.removeCallbacks(previewTimeout);
        worker.execute(()->preview.stop());
        super.onPause();
    }
    @Override protected void onDestroy(){worker.shutdown();super.onDestroy();}
    private void loadDevices() {
        try {
            JSONArray list=new JSONArray(repo.readDevices()==null?"[]":repo.readDevices());
            for(int i=0;i<list.length();i++) {
                JSONObject d=list.optJSONObject(i);
                if(d==null)continue;
                String host=d.optString("host","").trim();
                if(!host.isEmpty())devices.add(new WledDevice(host,d.optString("alias","")));
            }
        }catch(JSONException ignored){}
    }
    private int dp(int n){return Math.round(getResources().getDisplayMetrics().density*n);}
    private TextView label(String text,int size,int color){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(color);v.setPadding(dp(2),dp(4),dp(2),dp(4));return v;}
    private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private void pad(LinearLayout l,int all){l.setPadding(dp(all),dp(all),dp(all),dp(all));}
    private Button button(String title,Runnable action) {
        Button b=new Button(this);b.setText(title);b.setTextSize(13);b.setAllCaps(false);
        b.setTextColor(WHITE);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF24496D));
        b.setOnClickListener(v->action.run());b.setMinHeight(dp(48));return b;
    }
    private LinearLayout card(String heading,String detail) {
        LinearLayout box=vertical();pad(box,12);
        android.graphics.drawable.GradientDrawable back=new android.graphics.drawable.GradientDrawable();
        back.setColor(CARD);back.setCornerRadius(dp(14));back.setStroke(dp(1),0xFF315276);
        box.setBackground(back);
        box.addView(label(heading,17,WHITE));
        if(detail!=null&&!detail.isEmpty())box.addView(label(detail,12,MUTED));
        return box;
    }
    private void addCard(LinearLayout c){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(11);
        content.addView(c,lp);
    }
    private void buildShell() {
        LinearLayout root=vertical();root.setBackgroundColor(BG);pad(root,12);
        setContentView(root);
        LinearLayout header=row();
        TextView title=label("▦  Segment Studio",22,WHITE);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        header.addView(button("Done",this::finish),new LinearLayout.LayoutParams(dp(92),dp(48)));
        root.addView(header);
        status=label("Reading WLED segments…",12,MUTED);root.addView(status);
        LinearLayout tabs=row();
        tabs.addView(button("Devices & Segments",()->{zoneTab=false;render();}),new LinearLayout.LayoutParams(0,dp(52),1));
        tabs.addView(button("Multi-device Zones",()->{zoneTab=true;render();}),new LinearLayout.LayoutParams(0,dp(52),1));
        root.addView(tabs);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        content=vertical();pad(content,5);scroll.addView(content);
        render();
    }
    private void notifyStatus(String text) {
        if(status!=null) status.setText(text);
        android.widget.Toast.makeText(this,text,android.widget.Toast.LENGTH_SHORT).show();
    }
    private void refresh() {
        notifyStatus("Refreshing segment information…");
        worker.execute(()->{
            int online=0;
            for(WledDevice d:devices) {
                try{d.applySnapshot(WledApi.snapshot(d.host));online++;}catch(Exception ex){d.online=false;d.error=ex.getMessage();}
            }
            final int count=online;
            ui.post(()->{if(!isFinishing()){notifyStatus(count+"/"+devices.size()+" controllers connected");render();}});
        });
    }
    private void render() {
        if(content==null)return;
        content.removeAllViews();
        if(zoneTab)renderZones();else renderDevices();
    }
    private void renderDevices() {
        LinearLayout top=card("Physical WLED segments","Choose a segment to control, identify, rename, resize or remove. LED numbers start at 1.");
        top.addView(button("↻ Refresh devices",this::refresh));addCard(top);
        for(WledDevice d:devices) {
            LinearLayout c=card(d.name()+(d.online?"  ●":"  ○"),d.online?
                    d.ledCount+" LEDs · "+d.segments.size()+" segments · "+d.host:
                    "Offline · "+d.host);
            if(d.online) {
                for(WledDevice.Segment s:new ArrayList<>(d.segments)) {
                    String desc=s.label()+"  ·  LEDs "+(s.start+1)+"–"+s.stop+
                            "  ·  "+(s.on?"On":"Off")+"  ·  "+effectName(d,s.effect);
                    c.addView(button(desc,()->segmentActions(d,s)));
                }
                c.addView(button("+ Create segment",()->createSegment(d)));
                c.addView(button("Save layout as WLED preset…",()->saveLayout(d)));
            }
            addCard(c);
        }
        if(devices.isEmpty()) {
            LinearLayout no=card("No WLED controllers saved","Add controllers from the main GlowDeck Lights screen first.");addCard(no);
        }
    }
    private void renderZones() {
        LinearLayout info=card("Cross-controller zones","Group physical segments from any WLED controllers. Each range keeps its own segment and identity.");
        info.addView(button("+ Create a zone",this::createZone));
        info.addView(button("↻ Refresh devices",this::refresh));
        addCard(info);
        for(SegmentZone zone:new ArrayList<>(zones)) {
            StringBuilder members=new StringBuilder();
            for(SegmentZone.Member m:zone.members){if(members.length()>0)members.append("  ·  ");members.append(m.segmentName).append(" (").append(m.start+1).append("–").append(m.stop).append(")");}
            LinearLayout c=card(zone.name,zone.members.size()+" segments across devices\n"+members);
            c.addView(button("▶ Control this zone",()->zoneActions(zone)));
            c.addView(button("Identify selected LEDs",()->identify(zone.members)));
            c.addView(button("Edit members / name / delete",()->zoneEdit(zone)));
            addCard(c);
        }
    }
    private String effectName(WledDevice d,int fx) {return fx>=0&&fx<d.effects.size()?d.effects.get(fx):"Effect "+fx;}
    private void saveZones(){repo.saveZones(zones);render();}
    private void createZone() {
        final List<SegmentZone.Member> options=new ArrayList<>();
        final List<String> labels=new ArrayList<>();
        for(WledDevice d:devices)if(d.online)for(WledDevice.Segment s:d.segments) {
            options.add(SegmentZone.Member.of(d,s));
            labels.add(d.name()+" · "+s.label()+" · LEDs "+(s.start+1)+"–"+s.stop);
        }
        if(options.isEmpty()){notifyStatus("Connect a WLED controller with segments first");return;}
        final boolean[] selected=new boolean[options.size()];
        new AlertDialog.Builder(this).setTitle("Segments in new zone")
                .setMultiChoiceItems(labels.toArray(new String[0]),selected,(dialog,index,on)->selected[index]=on)
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Continue",(dialog,which)->{
                    SegmentZone zone=SegmentZone.create("New Zone");
                    for(int i=0;i<selected.length;i++)if(selected[i])zone.members.add(options.get(i));
                    if(zone.members.isEmpty()){notifyStatus("Select at least one segment");return;}
                    askText("Zone name","e.g. Entertainment Lighting",zone.name,name->{
                        if(name.trim().isEmpty())return;
                        zone.name=name.trim();zones.add(zone);saveZones();
                    });
                }).show();
    }
    private void zoneEdit(SegmentZone z) {
        new AlertDialog.Builder(this).setTitle(z.name)
                .setItems(new String[]{"Rename zone","Edit included segments","Delete zone"},(dlg,p)->{
                    if(p==0)askText("Rename zone","Zone name",z.name,value->{if(!value.trim().isEmpty()){z.name=value.trim();saveZones();}});
                    if(p==1){
                        // Membership editor does not rewrite physical segment boundaries.
                        final List<SegmentZone.Member> opts=new ArrayList<>();
                        final List<String> text=new ArrayList<>();
                        for(WledDevice d:devices)if(d.online)for(WledDevice.Segment s:d.segments){
                            opts.add(SegmentZone.Member.of(d,s));text.add(d.name()+" · "+s.label()+" · "+(s.start+1)+"–"+s.stop);
                        }
                        boolean[] checked=new boolean[opts.size()];
                        for(int i=0;i<opts.size();i++)for(SegmentZone.Member old:z.members)
                            if(old.deviceId.equals(opts.get(i).deviceId)&&old.segmentId==opts.get(i).segmentId)checked[i]=true;
                        new AlertDialog.Builder(this).setTitle("Members · "+z.name)
                                .setMultiChoiceItems(text.toArray(new String[0]),checked,(d,i,on)->checked[i]=on)
                                .setNegativeButton("Cancel",null).setPositiveButton("Save",(d,i)->{
                                    List<SegmentZone.Member> next=new ArrayList<>();
                                    for(int n=0;n<checked.length;n++)if(checked[n])next.add(opts.get(n));
                                    if(next.isEmpty()){notifyStatus("At least one member required");return;}
                                    z.members.clear();z.members.addAll(next);saveZones();
                                }).show();
                    }
                    if(p==2)new AlertDialog.Builder(this).setTitle("Delete zone?")
                            .setMessage("This only removes the GlowDeck zone. The WLED segments remain unchanged.")
                            .setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,i)->{zones.remove(z);saveZones();}).show();
                }).show();
    }
    private void segmentActions(WledDevice d,WledDevice.Segment s) {
        SegmentZone z=SegmentZone.create(s.label());z.members.add(SegmentZone.Member.of(d,s));
        new AlertDialog.Builder(this).setTitle(d.name()+" · "+s.label()+" · "+(s.start+1)+"–"+s.stop)
                .setItems(new String[]{"Control effect/colors/power","Identify selected LEDs","Rename segment","Resize range",
                        "Reverse animation direction","Remove segment","Add to existing zone"},(dlg,i)->{
                    if(i==0)zoneActions(z);
                    if(i==1)identify(z.members);
                    if(i==2)askText("Rename segment","Segment name",s.label(),name->{
                        if(!name.trim().isEmpty())mutate(d,"Rename segment",snap->WledSegmentPlanner.rename(snap,s.id,name.trim()),s.id,-1,-1,name.trim());
                    });
                    if(i==3)rangeDialog(d,s.start+1,s.stop,"Resize "+s.label(),(start,end)->
                            mutate(d,"Resize segment",snap->WledSegmentPlanner.resize(snap,s.id,start-1,end),s.id,start-1,end,null));
                    if(i==4)mutate(d,"Reverse segment",snap->WledSegmentPlanner.change(snap,s.id,
                            new JSONObject().put("rev",!s.reverse)),s.id,-1,-1,null);
                    if(i==5)new AlertDialog.Builder(this).setTitle("Remove "+s.label()+"?")
                            .setMessage("The WLED segment will be deleted. Zones referencing this segment may need updating.")
                            .setNegativeButton("Cancel",null).setPositiveButton("Delete",(x,y)->
                                    mutate(d,"Delete segment",snap->WledSegmentPlanner.delete(snap,s.id),s.id,-1,0,null)).show();
                    if(i==6)addToZone(SegmentZone.Member.of(d,s));
                }).show();
    }
    private void addToZone(SegmentZone.Member member) {
        if(zones.isEmpty()){notifyStatus("Create a zone from the Zones tab first");return;}
        String[] names=new String[zones.size()];for(int i=0;i<names.length;i++)names[i]=zones.get(i).name;
        new AlertDialog.Builder(this).setTitle("Add segment to zone").setItems(names,(dialog,i)->{
            SegmentZone z=zones.get(i);
            for(SegmentZone.Member m:z.members)if(m.deviceId.equals(member.deviceId)&&m.segmentId==member.segmentId){notifyStatus("Already in zone");return;}
            z.members.add(member);saveZones();notifyStatus("Added to "+z.name);
        }).show();
    }
    private void createSegment(WledDevice d) {
        WledDevice.Segment parent=d.segments.isEmpty()?null:d.segments.get(0);
        if(parent==null){notifyStatus("No base segment. Configure LED output in WLED first.");return;}
        rangeDialog(d,parent.start+1,parent.stop,"New segment · "+d.name(),(start,end)->{
            askText("Name new segment","e.g. Cabinet right","New Segment",name->{
                if(name.trim().isEmpty())return;
                // Confirm split explicitly; preserve outside ranges.
                new AlertDialog.Builder(this).setTitle("Split segment and create range?")
                        .setMessage("Create "+name+" at LEDs "+start+"–"+end+
                                ". WLED may split the existing section to keep the remaining LEDs controlled.\n\n"+
                                "The change is sent only after confirmation. To persist across WLED reboots, save a WLED preset with segment bounds.")
                        .setNegativeButton("Cancel",null)
                        .setPositiveButton("Create",(dialog,which)->mutate(d,"Create segment",
                                snap->WledSegmentPlanner.splitToCreate(snap,start-1,end,name.trim()),-1,-1,-1,null))
                        .show();
            });
        });
    }
    private interface RangeDone {void selected(int startInclusive,int endInclusive);}
    private void rangeDialog(WledDevice device,int first,int last,String title,RangeDone callback) {
        // Numeric fields and remote-friendly step buttons for phone, tablet and Fire TV.
        LinearLayout body=vertical();pad(body,16);
        EditText start=new EditText(this),end=new EditText(this);
        start.setInputType(InputType.TYPE_CLASS_NUMBER);end.setInputType(InputType.TYPE_CLASS_NUMBER);
        start.setText(String.valueOf(first));end.setText(String.valueOf(last));
        body.addView(label("Start LED (inclusive)",14,WHITE));body.addView(start,new LinearLayout.LayoutParams(-1,dp(52)));
        stepButtons(body,start,device.ledCount);
        body.addView(label("End LED (inclusive)",14,WHITE));body.addView(end,new LinearLayout.LayoutParams(-1,dp(52)));
        stepButtons(body,end,device.ledCount);
        body.addView(label("Identify temporarily lights up this range in amber, then restores the original effects.",12,MUTED));
        body.addView(button("Identify selected LED range (12 seconds)",()->{
            try {
                int a=Integer.parseInt(start.getText().toString()), b=Integer.parseInt(end.getText().toString());
                if(a<1||b<a||b>device.ledCount)throw new Exception("Invalid LED range");
                WledDevice.Segment owner=null;
                for(WledDevice.Segment s:device.segments)if(s.start<=a-1 && s.stop>=b){owner=s;break;}
                if(owner==null)throw new Exception("Range must fit an existing segment to preview it");
                List<SegmentZone.Member> selection=new ArrayList<>();
                selection.add(new SegmentZone.Member(device.identity(),device.host,owner.id,a-1,b,
                        "LEDs "+a+"–"+b));
                identify(selection);
            }catch(Exception ex){notifyStatus("Cannot identify: "+ex.getMessage());}
        }));
        new AlertDialog.Builder(this).setTitle(title).setView(body)
                .setNegativeButton("Cancel",null).setPositiveButton("Continue",(dialog,which)->{
                    try{
                        int a=Integer.parseInt(start.getText().toString()),b=Integer.parseInt(end.getText().toString());
                        if(a<1||b<a||b>device.ledCount)throw new Exception("Range must be 1–"+device.ledCount);
                        callback.selected(a,b);
                    }catch(Exception ex){notifyStatus("Invalid LED range: "+ex.getMessage());}
                }).show();
    }
    private void stepButtons(LinearLayout target,EditText field,int max) {
        LinearLayout steps=row();
        for(int delta:new int[]{-10,-1,1,10})steps.addView(button(delta>0?"+"+delta:String.valueOf(delta),()->{
            try{int n=Integer.parseInt(field.getText().toString());field.setText(String.valueOf(Math.min(max,Math.max(1,n+delta))));}
            catch(Exception ex){field.setText("1");}
        }),new LinearLayout.LayoutParams(0,dp(48),1));
        target.addView(steps);
    }
    private interface PatchBuilder { JSONObject build(JSONObject snapshot)throws Exception; }
    private void mutate(WledDevice d,String description,PatchBuilder build,int segmentId,int start,int stop,String name) {
        notifyStatus(description+" · Sending request…");
        worker.execute(()->{
            String feedback;
            try {
                JSONObject before=WledApi.snapshot(d.host);
                String identity=GlowScene.identity(before.optJSONObject("info"),d.host);
                if(!d.identity().startsWith("host:")&&!d.identity().equals(identity))
                    throw new Exception("Controller identity changed; refresh before editing");
                JSONObject change=build.build(before);
                WledApi.send(d.host,change);
                feedback=description+" · Sent, checking WLED…";
                final String message=feedback;ui.post(()->notifyStatus(message));
                boolean matched=false;
                for(int attempts=0;attempts<4;attempts++) {
                    JSONObject after=WledApi.snapshot(d.host);
                    if(segmentId>=0) {
                        WledSegmentPlanner.Part part=WledSegmentPlanner.find(after,segmentId);
                        matched=stop==0?part==null:
                                part!=null&&(start<0||part.start==start&&part.stop==stop)&&
                                (name==null||name.equals(part.data.optString("n")));
                    } else {
                        matched=change.getJSONArray("seg").length()>0 &&
                                // Layout mutations can allocate new IDs: ensure every expected start/stop is present.
                                layoutMatches(change.optJSONArray("seg"),after);
                    }
                    if(matched)break;
                    Thread.sleep(230);
                }
                feedback=matched?description+" · Confirmed":description+" · Sent but not confirmed";
                d.applySnapshot(WledApi.snapshot(d.host));
            }catch(Exception ex){feedback=description+" · Failed: "+ex.getMessage();}
            final String done=feedback;ui.post(()->{notifyStatus(done);render();});
        });
    }
    private boolean layoutMatches(JSONArray expected,JSONObject snapshot) {
        if(expected==null)return false;
        for(int i=0;i<expected.length();i++){
            JSONObject s=expected.optJSONObject(i);if(s==null)return false;
            WledSegmentPlanner.Part part=WledSegmentPlanner.find(snapshot,s.optInt("id",-1));
            if(part==null)return false;
            if(s.has("start")&&(part.start!=s.optInt("start")||part.stop!=s.optInt("stop")))return false;
        }
        return true;
    }
    private void zoneActions(SegmentZone zone) {
        new AlertDialog.Builder(this).setTitle("Control · "+zone.name)
                .setItems(new String[]{"Power ON","Power OFF","Brightness","Choose effect","Choose palette",
                        "Primary (Fx) color","Background (Bg) color","Tertiary (Cs) color","Speed","Intensity","Identify LEDs"},(dlg,i)->{
                    if(i==0||i==1)zoneApply(zone,new JSONObjectBuilder().put("on",i==0).make(),null,-1,null);
                    if(i==2||i==9||i==8){int key=i;
                        int[] amounts=i==2?new int[]{25,50,75,100}:new int[]{25,50,75,100};
                        new AlertDialog.Builder(this).setTitle(i==2?"Brightness":i==8?"Speed":"Intensity")
                                .setItems(new String[]{"25%","50%","75%","100%"},(d,j)->{
                                    String property=key==2?"bri":key==8?"sx":"ix";
                                    zoneApply(zone,new JSONObjectBuilder().put(property,Math.max(1,Math.round(amounts[j]*255f/100))).make(),null,-1,null);
                                }).show();
                    }
                    if(i==3||i==4)chooseZoneName(zone,i==3);
                    if(i>=5&&i<=7)pickColor(zone,i-5);
                    if(i==10)identify(zone.members);
                }).show();
    }
    private static final class JSONObjectBuilder {
        private final JSONObject item=new JSONObject();
        JSONObjectBuilder put(String name,Object val){try{item.put(name,val);}catch(Exception ignored){}return this;}
        JSONObject make(){return item;}
    }
    private void chooseZoneName(SegmentZone z,boolean effect) {
        List<String> options=new ArrayList<>();
        for(WledDevice d:devices)if(d.online) {
            List<String> names=effect?d.effects:d.palettes;
            for(String n:names)if(n!=null&&!n.isEmpty()&&!n.equals("-")&&!n.equals("RSVD")&&!options.contains(n))options.add(n);
        }
        if(options.isEmpty()){notifyStatus("Connect a light to load effects and palettes");return;}
        String[] list=options.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle(effect?"Effect for "+z.name:"Palette for "+z.name)
                .setItems(list,(dialog,i)->{
                    if(effect)zoneApply(z,new JSONObject(),list[i],-1,null);
                    else zoneApply(z,new JSONObject(),null,-1,null,list[i]);
                }).setNegativeButton("Cancel",null).show();
    }
    private void pickColor(SegmentZone zone,int slot) {
        final int[] colors={0xFFFFA75F,0xFFFFFFFF,0xFFFF414F,0xFFFF842F,0xFFFFD94B,0xFF5DE08E,0xFF3E9BFF,0xFFB178FF};
        String[] names={"Warm","White","Red","Orange","Yellow","Green","Blue","Purple","Custom RGB…"};
        new AlertDialog.Builder(this).setTitle(new String[]{"Primary color","Background color","Tertiary color"}[slot])
                .setItems(names,(d,i)->{
                    if(i<colors.length)zoneApply(zone,new JSONObject(),null,slot,colors[i]);
                    else colorSliders(zone,slot);
                }).show();
    }
    private void colorSliders(SegmentZone z,int slot) {
        LinearLayout box=vertical();pad(box,18);
        int[] value={255,160,80};
        for(int channel=0;channel<3;channel++){
            int ci=channel;box.addView(label(new String[]{"Red","Green","Blue"}[channel],14,WHITE));
            SeekBar bar=new SeekBar(this);bar.setMax(255);bar.setProgress(value[channel]);
            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}
                public void onProgressChanged(SeekBar s,int progress,boolean user){value[ci]=progress;}
            });box.addView(bar,new LinearLayout.LayoutParams(-1,dp(48)));
        }
        new AlertDialog.Builder(this).setTitle("Custom zone color").setView(box)
                .setNegativeButton("Cancel",null).setPositiveButton("Apply",(d,i)->zoneApply(z,new JSONObject(),null,slot,Color.rgb(value[0],value[1],value[2]))).show();
    }
    private void zoneApply(SegmentZone z,JSONObject state,String effect,int slot,Integer rgb) {zoneApply(z,state,effect,slot,rgb,null);}
    private void zoneApply(SegmentZone z,JSONObject state,String effect,int slot,Integer rgb,String palette) {
        notifyStatus(z.name+" · Request queued…");
        worker.execute(()->{
            SegmentZoneController.Result result=SegmentZoneController.execute(z,new ArrayList<>(devices),state,
                    effect,slot,rgb,WledApi::snapshot,(host,patch)->{
                        if(palette!=null){
                            int id=SegmentZoneController.findName(WledApi.snapshot(host).optJSONArray("palettes"),palette);
                            if(id<0)throw new Exception("Palette unsupported: "+palette);
                            JSONArray seg=patch.optJSONArray("seg");
                            if(seg!=null)for(int i=0;i<seg.length();i++)seg.getJSONObject(i).put("pal",id);
                        }
                        WledCommandVerifier.sendAndVerify(host,patch,WledApi::send,WledApi::snapshot,
                                WledApi::presets, sent->ui.post(()->notifyStatus(z.name+" · Sent; checking WLED…")));
                    });
            ui.post(()->notifyStatus(z.name+" · "+result.summary()+
                    (result.problems.isEmpty()?"":" · "+result.problems.get(0))));
            for(WledDevice d:devices)try{d.applySnapshot(WledApi.snapshot(d.host));}catch(Exception ignored){}
            ui.post(this::render);
        });
    }
    private void identify(List<SegmentZone.Member> members) {
        if(members.isEmpty())return;
        ui.removeCallbacks(previewTimeout);
        notifyStatus("Starting temporary LED identification…");
        worker.execute(()->{
            String outcome=preview.begin(members,new ArrayList<>(devices));
            ui.post(()->{
                notifyStatus(outcome);
                if(preview.active()) {
                    ui.postDelayed(previewTimeout,12000);
                    new AlertDialog.Builder(this).setTitle("Identify LEDs · 12 seconds")
                            .setMessage("Selected LEDs are temporarily highlighted in amber. Stop to restore their previous effects. This does not create permanent segments.")
                            .setPositiveButton("Stop & restore",(dialog,which)->stopPreview("Identification stopped"))
                            .setOnDismissListener(dialog->stopPreview("Restoring lighting"))
                            .show();
                }
            });
        });
    }
    private void stopPreview(String reason) {
        ui.removeCallbacks(previewTimeout);
        worker.execute(()->{
            String restore=preview.stop();
            ui.post(()->notifyStatus(reason+" · "+restore));
        });
    }
    private void saveLayout(WledDevice d) {
        new AlertDialog.Builder(this).setTitle("Save WLED segment bounds")
                .setMessage("Persist this controller's current layout to a WLED preset, with Save Segment Bounds enabled. Pick an unused preset ID (1–250). This may overwrite an existing preset of the same ID.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Choose preset ID",(dialog,which)->askText("Preset ID (1–250)","e.g. 200","200",value->{
                    try{
                        int id=Integer.parseInt(value.trim());if(id<1||id>250)throw new Exception();
                        notifyStatus("Saving layout preset "+id+"…");
                        worker.execute(()->{
                            try{
                                JSONObject patch=new JSONObject().put("psave",id).put("sb",true)
                                        .put("n","GlowDeck Segment Layout");
                                WledCommandVerifier.sendAndVerify(d.host,patch,WledApi::send,
                                        WledApi::snapshot,WledApi::presets,null);
                                ui.post(()->notifyStatus("WLED layout preset "+id+" confirmed"));
                            }catch(Exception e){ui.post(()->notifyStatus("Preset save failed: "+e.getMessage()));}
                        });
                    }catch(Exception ex){notifyStatus("Preset ID must be 1–250");}
                })).show();
    }
    private interface TextDone {void accept(String text);}
    private void askText(String title,String hint,String initial,TextDone done) {
        EditText edit=new EditText(this);edit.setText(initial);edit.setHint(hint);edit.setSingleLine(true);
        LinearLayout body=vertical();pad(body,18);body.addView(edit,new LinearLayout.LayoutParams(-1,dp(60)));
        new AlertDialog.Builder(this).setTitle(title).setView(body).setNegativeButton("Cancel",null)
                .setPositiveButton("Save",(dialog,i)->done.accept(edit.getText().toString())).show();
    }
}
