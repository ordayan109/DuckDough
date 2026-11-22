// Capture numerical fixtures from a separately downloaded public reference script.
// Usage: node tools/capture-reference.cjs reference.js output.csv
// The third-party calculator source is deliberately not copied into this project.
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const assert = require('node:assert/strict');
const [sourcePath, outputPath] = process.argv.slice(2);
if (!sourcePath || !outputPath) throw new Error('Expected reference script path and CSV output path');
const elements = new Map();
const get = id => {
  if (!elements.has(id)) elements.set(id, {value:'',checked:false,textContent:'',innerHTML:'',classList:{add(){},remove(){},toggle(){}},addEventListener(){}});
  return elements.get(id);
};
get('ps').value='16'; get('pq').value='6';
const sandbox = {document:{getElementById:get,querySelectorAll:()=>[]},window:{},navigator:{}};
vm.runInNewContext(fs.readFileSync(sourcePath,'utf8'),sandbox,{timeout:1000});
const rows=['inches,pizzas,thickness,gluten_free,flour,water,yeast,salt,sugar,oil,original_ball'];
for (let inches=10;inches<=20;inches++) for (let pizzas=1;pizzas<=10;pizzas++) {
  for (const [thickness,factor] of [['THIN',1.8],['REGULAR',2.11],['THICK',2.75]]) for (const gf of [false,true]) {
    get('ps').value=String(inches); get('pq').value=String(pizzas); get('gft').checked=gf;
    sandbox.window.dgSt({dataset:{v:String(factor)},classList:{add(){}}});
    const amounts=[...get('ibody').innerHTML.matchAll(/<td>([\d.]+)<span class="dg-iu">/g)].map(match=>Number(match[1]));
    assert.equal(amounts.length,6);
    rows.push([inches,pizzas,thickness,gf,...amounts,Number(get('rball').textContent)].join(','));
  }
}
fs.mkdirSync(path.dirname(outputPath),{recursive:true});
fs.writeFileSync(outputPath,rows.join('\n')+'\n');
console.log(JSON.stringify({cases:rows.length-1,output:outputPath}));
