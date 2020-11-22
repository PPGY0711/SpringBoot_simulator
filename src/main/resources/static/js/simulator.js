// 模拟器实现
$(document).ready(function(){
    //页面主要功能部件
    var romFileName = document.getElementById("ROMFileName");
    var romFile = document.getElementById("ROMFile");
    var romFileBtn = document.getElementById("openROMFileBtn");
    var desktop = document.getElementById("desktop");
    var statusBar = document.getElementById("statusBar");
    var memoryTab = document.getElementById("memory");
    var programTab = document.getElementById("program");
    var descriptionTab = document.getElementById("description");
    var machineCodeTab = document.getElementById("machineCode");
    var terminalTab = document.getElementById("terminal");
    var memoryArea = document.getElementById("memoryArea");
    var programArea = document.getElementById("programArea");
    var machineCodeArea = document.getElementById("machineCodeArea");
    var terminalArea = document.getElementById("terminalArea");
    var coprocessorTbl = document.getElementById("coprocessorTbl");
    var controlPanelArea = document.getElementById("controlPanel");
    var nextPC = document.getElementById("PC");
    var instruction = document.getElementById("instruction");
    var debugBtn = document.getElementById("debugBtn");
    var executeBtn = document.getElementById("executeBtn");
    var debugDiv = document.getElementById("debugDiv");
    var nextBtn = document.getElementById("nextBtn");
    var stopBtn = document.getElementById("stopBtn");
    var rebootBtn = document.getElementById("reboot");
    var crumbNav = document.getElementById("crumbNav");
    var crumbNavItems = crumbNav.getElementsByTagName("li");
    var tip = document.getElementById('tip');

    rebootBtn.onclick = reboot;
    rebootBtn.click();

    for(var i = 0;i<crumbNavItems.length;i++){
        var li = crumbNavItems[i];
        li.index = i;
        li.onclick = function(){
            this.className = "active";
            for(var j = 0; j < crumbNavItems.length; j++){
                if(j !== this.index)
                    crumbNavItems[j].className = "";
            }
            switch (this.index) {
                case 0:
                {
                    memoryTab.style.display="block";
                    machineCodeTab.style.display="none";
                    programTab.style.display="none";
                    descriptionTab.style.display="none";
                    terminalTab.style.display="none";
                    break;
                }
                case 1:
                {
                    memoryTab.style.display="none";
                    machineCodeTab.style.display="none";
                    programTab.style.display="block";
                    descriptionTab.style.display="none";
                    terminalTab.style.display="none";
                    break;
                }
                case 2:
                {
                    memoryTab.style.display="none";
                    machineCodeTab.style.display="none";
                    programTab.style.display="none";
                    descriptionTab.style.display="block";
                    terminalTab.style.display="none";
                    break;
                }
                case 3:
                {
                    memoryTab.style.display="none";
                    machineCodeTab.style.display="block";
                    programTab.style.display="none";
                    descriptionTab.style.display="none";
                    terminalTab.style.display="none";
                    break;
                }
                case 4:
                {
                    memoryTab.style.display="none";
                    machineCodeTab.style.display="none";
                    programTab.style.display="none";
                    descriptionTab.style.display="none";
                    terminalTab.style.display="block";
                    break;
                }
            }
        };
    }
    //设置状态栏计时器
    function timeCount() {
        statusBar.value = formatDate(new Date().getTime());
    }

    function setStatusBar(){
        timeCount();
        setInterval(timeCount,60000);
    }

    //设置时间格式
    function formatDate(time){
        var date = new Date(time);
        var year = date.getFullYear(),
            month = date.getMonth()+1,//月份是从0开始的
            day = date.getDate(),
            hour = date.getHours(),
            min = date.getMinutes();
        return year + '-' +
            (month < 10? '0' + month : month) + '-' +
            (day < 10? '0' + day : day) + ' ' +
            (hour < 10? '0' + hour : hour) + ':' +
            (min < 10? '0' + min : min);
    }

    function reboot(){
        setStatusBar();
        //重启：初始化MIPS模拟器参数->读入MipsOS.bin到内存->初始化寄存器->初始化control panel->初始化显示
        $.ajax({
            url: "/rebootSimulator",
            type: "POST",
            success: function (data) {
                // data = JSON.parse(data);
                console.log(data);
                refreshBoard(data);
                // desktop.value = data['displayStr'];
            }
        });
        // debugBtn.removeAttribute("disabled");
        executeBtn.removeAttribute("disabled");
        nextBtn.removeAttribute("disabled");
        machineCodeArea.removeAttribute("readonly");
        stopBtn.disabled="disabled";
        debugDiv.style.display = "none";
        tip.style.display="none";
        desktop.value = "";
    }

    function refreshBoard(data){
        var commonReg = data['commonRegisterContent'];
        updateCommonRegisterContent(commonReg);
        var coprocessorReg = data['coprocessorRegisterContent'];
        updateCoprocessorRegisterContent(coprocessorReg);
        var controlPanelInfo = data['controlPanelInfo'];
        updateControlPanel(controlPanelInfo);
        var nextInstructionInfo = data['nextInstructionInfo'];
        updateNextActionPanel(nextInstructionInfo);
        memoryArea.value = data['memoryInfo'];
        programArea.value = data['programInfo'];
        desktop.value = data['dispStr'];
    }

    function updateControlPanel(info) {
        var str = "";
        str += "[instr]: " + info['instr'] + "\r\n";
        str += "[op   ]: " + info['op'] +"\r\n";
        str += "[rs   ]: " + "$" + info['rs'] + "\r\n";
        str += "[rt   ]: " + "$" + info['rt'] + "\r\n";
        str += "[rd   ]: " + "$" + info['rd'] + "\r\n";
        str += "[shmt ]: " + info['sa'] + "\r\n";
        str += "[data ]: " + "[    "+ info['dat'].toString(16)+"]" +info['dat'] + "\r\n";
        str += "[addr ]: " + "[    "+ info['adr'].toString(16)+"]" + info['adr'] + "\r\n";
        str += "[memry]: " + "[    "+ info['memory'].toString(16)+"]" +info['memory'] + "\r\n";
        controlPanelArea.value = str;
    }

    function updateCommonRegisterContent(contents) {
        for(var i=0;i< contents.length;i++){
            // console.log("reg"+contents[i]['id']);
            var td = document.getElementById("reg"+contents[i]['id']);
            td.innerHTML = contents[i]['name'] + ": " + contents[i]['content'].toString(16);
        }
    }

    function updateCoprocessorRegisterContent(contents){
        var tds = coprocessorTbl.getElementsByTagName("td");
        for(var i=0;i< contents.length;i++){
            tds[contents[i]['id']].innerHTML = contents[i]['content'].toString(16);
        }
    }

    function updateNextActionPanel(info){
        nextPC.value = info['pc'];
        instruction.value = info['inst'];
    }

    romFile.onclick = handleFile;

    romFileBtn.onclick = function(){
        tip.style.display='none';
        romFile.click();
    };

    function handleFile(){
        //文件读取处理
        var file = document.getElementById('ROMFile');
        var fileName = "";
        file.onchange = function(e){
            var files = e.target.files;
            //选取要读取的文件
            var file0 = files[0];
            fileName = file0.name;
            var suffix = fileName.substring(fileName.lastIndexOf('.')+1);
            if(suffix !== 'bin')
            {
                tip.innerText = "无法接收以" + suffix + "为后缀的文件作为输入！";
                tip.style.display='block';
            }
            else{
                var fileReader = new FileReader();
                // 发送异步请求
                fileReader.readAsArrayBuffer(file0);
                // 读取失败
                fileReader.onerror = function(){
                    tip.innerText = "无法读取文件" + fileName + "，请重试！";
                };
                // 读取成功，数据保存在fileReader对象的result属性中
                fileReader.onload = function(){
                    var bytes = fileReader.result;
                    romFileName.value = fileName;
                    var array = new Int8Array(bytes);
                    var jsonArray = [];
                    for(var i = 0; i < array.length; i++){
                        jsonArray.push({"id":i,"value":array[i]});
                    }
                    $.ajax({
                        type: "POST",
                        data: JSON.stringify({binData: jsonArray}),
                        url: "/sendBinFile",
                        dataType: "text",
                        contentType: 'application/json;charset=UTF-8',
                        success: function (data) {
                            console.log(data);
                            data = JSON.parse(data);
                            if(!data['errorMsg']){
                                refreshBoard(data);
                            }
                            machineCodeArea.value = "";
                        },
                        error: function(){
                            console.log("file data send failed.")
                        }
                    });
                }
            }
        };
    }

    function getTxtCursorPosition(textAreaId){
        var textArea = document.getElementById(textAreaId);
        var cursorPosition=0;
        if(textArea.selectionStart){//非IE
            cursorPosition= textArea.selectionStart;
        }else{//IE
            try{
                var range = document.selection.createRange();
                range.moveStart("character",-textArea.value.length);
                cursorPosition=range.text.length;
            }catch(e){
                cursorPosition = 0;
            }
        }
        return cursorPosition;
    }

    // 使Tab键能输入
    machineCodeArea.addEventListener("keydown", function (event) {
        var index = getTxtCursorPosition("machineCodeArea");
        var txt = this.value;
        if (event.key !== undefined) {
            if(event.key === "Tab"){
                if (!this.value) this.value= '';
                this.value = txt.slice(0, index) + "\t" + txt.slice(index);
                // 阻止默认切换元素的行为
                if (event && event.preventDefault) {
                    event.preventDefault()
                } else {
                    window.event.returnValue = false;
                }
            }
            if(event.key === "Enter"){
                if (!this.value) this.value= '';
                this.value = txt.slice(0, index) + "\r\n" + txt.slice(index);
                // 阻止默认切换元素的行为
                if (event && event.preventDefault) {
                    event.preventDefault()
                } else {
                    window.event.returnValue = false;
                }
            }
        } else if (event.keyCode !== undefined) {
            if (event.keyCode === 9) {
                if (!this.value) this.value= '';
                txt.replace(txt.substring(0,index), txt.substring(0,index)+"\t");
                this.value = txt;
                alert(index + "," + this);
                // 阻止默认切换元素的行为
                if (event && event.preventDefault) {
                    event.preventDefault()
                } else {
                    window.event.returnValue = false;
                }
            }
        }
    }, true);

    executeBtn.onclick = function () {
        $.ajax({
            type: "POST",
            data: JSON.stringify({data: machineCodeArea.value}),
            url: "/executeMachineCode",
            dataType: "text",
            contentType: 'application/json;charset=UTF-8',
            success: function (data) {
                console.log(data);
                data = JSON.parse(data);
                if(!data['errorMsg']  && data['errorMsg']!==null){
                    refreshBoard(data);
                }
                else {
                    console.log(data['errorMsg']);
                    if(data['intRequest']){
                        handleInterruptRequest(data['intRequest']);
                    }
                }
            },
            error: function () {
                console.log("send MachineCode failed!");
            }
        })
    };

    function handleInterruptRequest(intRequest) {
        //产生中断
        crumbNavItems[4].click();
        terminalArea.removeAttribute("readonly");
        tip.style.display = "block";
        switch (intRequest) {
            case 5:
            {
                tip.innerHTML = "请在Terminal输入一个十进制整数（范围在int类型内）";
                terminalArea.index = 5;
                break;
            }
            case 8:
            {
                tip.innerHTML = "请在Terminal输入一个字符串";
                terminalArea.index = 8;
                break;
            }
            case 12:
            {
                tip.innerHTML = "请在Terminal输入一个字符（中英文皆可）";
                terminalArea.index = 12;
                break;
            }
        }
    }

    terminalArea.addEventListener("keydown",function (ev) {
        var index = getTxtCursorPosition("terminalArea");
        var txt = this.value;
        if (event.key !== undefined) {
            if(event.key === "Tab"){
                if (!this.value) this.value= '';
                this.value = txt.slice(0, index) + "\t" + txt.slice(index);
                // 阻止默认切换元素的行为
                if (event && event.preventDefault) {
                    event.preventDefault()
                } else {
                    window.event.returnValue = false;
                }
            }
            if(event.key === "Enter"){
                if (!this.value) this.value= '';
                var newInput = "";
                if(txt.lastIndexOf("\n")!==-1)
                    newInput = txt.slice(txt.lastIndexOf("\n"));
                else
                    newInput = txt;
                var status = 0;
                if(debugDiv.style.display === "block")
                    status = 1;
                $.ajax({
                    url: "/handleInterruptRequest",
                    type: "POST",
                    data: JSON.stringify({data: newInput, intRequest: this.index, status: status}),
                    dataType: "text",
                    contentType: 'application/json;charset=UTF-8',
                    success: function (data) {
                        console.log(data);
                        data=JSON.parse(data);
                        if(data['refreshData']){
                            refreshBoard(data['refreshData']);
                        }
                        console.log(data['msg']);
                    }
                });
                this.value = txt.slice(0, index) + "\r\n" + txt.slice(index);
                this.readonly = "readonly";
                tip.style.display = "none";
                // 阻止默认切换元素的行为
                if (event && event.preventDefault) {
                    event.preventDefault()
                } else {
                    window.event.returnValue = false;
                }
            }
        } else if (event.keyCode !== undefined) {
            if (event.keyCode === 9) {
                if (!this.value) this.value= '';
                txt.replace(txt.substring(0,index), txt.substring(0,index)+"\t");
                this.value = txt;
                alert(index + "," + this);
                // 阻止默认切换元素的行为
                if (event && event.preventDefault) {
                    event.preventDefault()
                } else {
                    window.event.returnValue = false;
                }
            }
        }
    }, true);

    var beforeExecute = null;
    var dataSize = -1;

    debugBtn.onclick=function(){
        $.ajax({
            type: "POST",
            data: JSON.stringify({data: machineCodeArea.value}),
            url: "/enterDebugMode",
            dataType: "text",
            contentType: 'application/json;charset=UTF-8',
            success: function (data) {
                console.log(data);
                data = JSON.parse(data);
                if(!data['errorMsg']){
                    refreshBoard(data);
                    beforeExecute = data;
                    machineCodeArea.readonly = "readonly";
                    debugDiv.style.display="block";
                    executeBtn.disabled = "disabled";
                    nextBtn.removeAttribute("disabled");
                    dataSize = data['dataSize'];
                }
                else {
                    console.log(data['errorMsg']);
                }
            },
            error: function () {
                console.log("Enter Debug Mode failed!");
            }
        })
    };

    nextBtn.onclick = function(){
        $.ajax({
            type: "POST",
            url: "/singleStepDebug",
            data: JSON.stringify({dataSize: dataSize}),
            dataType: "text",
            contentType: 'application/json;charset=UTF-8',
            success: function (data) {
                console.log(data);
                data = JSON.parse(data);
                if(!data['errorMsg'] && data['errorMsg']!==null){
                    refreshBoard(data);
                    if(data['end'] === true){
                        executeBtn.removeAttribute("disabled");
                        nextBtn.disabled= "disabled";
                        stopBtn.removeAttribute("disabled");
                        console.log("end");
                    }
                }
                else {
                    console.log(data['errorMsg']);
                    if(data['intRequest']){
                        //产生中断
                        handleInterruptRequest(data['intRequest']);
                    }
                }
            },
            error: function () {
                console.log("Debug failed!");
            }
        })
    };

    stopBtn.onclick = function(){
        executeBtn.removeAttribute("disabled");
        machineCodeArea.removeAttribute("readonly");
        stopBtn.disabled = "disabled";
        debugDiv.style.display = "none";
    }
});