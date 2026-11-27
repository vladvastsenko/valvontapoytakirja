"use strict";
function nextAttempt(previous,now){
 const windowMs=600000;
 if(!previous||now-previous.start>=windowMs) return {start:now,count:1};
 if(previous.count>=5) return null;
 return {start:previous.start,count:previous.count+1};
}
module.exports={nextAttempt};
