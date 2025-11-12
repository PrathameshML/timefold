steps of project setup and run
1) Prerequisites:
   A)Install Java 21 (JDK)
   B)Install Maven 

2) Create Backend(TimeFold + Quarkus):
   A)Create Project by using this Command:npx quarkus-cli create app com.scheduler:timefold-solver --extension=rest,resteasy-reactive-jackson,timefold-solver
   B) Go to folder: cd timefold-solver
   D)Run backend by using this Command ./mvnw quarkus:dev ,If using Intellij click on play button on top right corner

3) Create Frontend(Bryntum + React)
   A)Create React app: npx create-react-app bryntum-scheduler
   B)Go to folder :cd bryntum-scheduler 
   C)Install Bryntum Scheduler:npm install @bryntum/scheduler
   D)Copy these files into src/: app.jsx,index.css,index.js

4) Run the frontend by using npm start or for Production build npm run build and npx serve -s build