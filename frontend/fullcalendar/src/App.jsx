import React, { useEffect, useRef, useState, useCallback } from 'react';

import FullCalendar from '@fullcalendar/react';
import dayGridPlugin from '@fullcalendar/daygrid';
import timeGridPlugin from '@fullcalendar/timegrid';
import interactionPlugin from '@fullcalendar/interaction';
import resourceTimelinePlugin from '@fullcalendar/resource-timeline';
// import '@fullcalendar/core/main.css';
// import '@fullcalendar/daygrid/main.css';
// import '@fullcalendar/timegrid/main.css';
// import '@fullcalendar/timeline/main.css';
// import '@fullcalendar/resource-timeline/main.css';

// Add global styles for common scrollbar and theme
const globalStyles = `
  /* Common scrollbar styling for the entire app */
  .common-scrollbar-container {
    height: 100vh;
    overflow: hidden;
    display: flex;
    flex-direction: column;
    font-family: Arial, sans-serif;
    width: 100%;
    transition: all 0.3s ease;
  }

  .common-scrollbar-content {
    flex: 1;
    overflow-y: auto;
    padding: 20px;
    width: 100%;
    box-sizing: border-box;
    transition: all 0.3s ease;
  }

  /* Light theme */
  .light-theme {
    background-color: #f5f5f5;
    color: #333;
  }

  .light-theme .common-scrollbar-content {
    background-color: #f5f5f5;
  }

  /* Dark theme */
  .dark-theme {
    background-color: #1a1a1a;
    color: #ffffff;
  }

  .dark-theme .common-scrollbar-content {
    background-color: #1a1a1a;
  }

  /* Custom scrollbar styling */
  .common-scrollbar-content::-webkit-scrollbar {
    width: 12px;
  }

  .common-scrollbar-content::-webkit-scrollbar-track {
    background: #f1f1f1;
    border-radius: 6px;
  }

  .common-scrollbar-content::-webkit-scrollbar-thumb {
    background: #c1c1c1;
    border-radius: 6px;
    border: 2px solid #f1f1f1;
  }

  .common-scrollbar-content::-webkit-scrollbar-thumb:hover {
    background: #a8a8a8;
  }

  /* Dark theme scrollbar */
  .dark-theme .common-scrollbar-content::-webkit-scrollbar-track {
    background: #2d2d2d;
  }

  .dark-theme .common-scrollbar-content::-webkit-scrollbar-thumb {
    background: #555;
    border: 2px solid #2d2d2d;
  }

  .dark-theme .common-scrollbar-content::-webkit-scrollbar-thumb:hover {
    background: #777;
  }

  /* Firefox scrollbar */
  .common-scrollbar-content {
    scrollbar-width: thin;
    scrollbar-color: #c1c1c1 #f1f1f1;
  }

  .dark-theme .common-scrollbar-content {
    scrollbar-color: #555 #2d2d2d;
  }

  /* Remove individual scrollbars from panels */
  .no-individual-scroll {
    overflow: visible !important;
    max-height: none !important;
  }

  /* Adjust panel heights to fit within common scroll */
  .auto-height-panel {
    height: auto !important;
    max-height: none !important;
    overflow: visible !important;
    width: 100% !important;
    box-sizing: border-box;
  }

  /* Sticky header */
  .sticky-header {
    position: sticky;
    top: 0;
    z-index: 100;
    padding: 20px 0;
    border-bottom: 2px solid #e9ecef;
    box-shadow: 0 2px 4px rgba(0,0,0,0.1);
    width: 100%;
    box-sizing: border-box;
    transition: all 0.3s ease;
  }

  .light-theme .sticky-header {
    background: white;
    border-bottom-color: #e9ecef;
  }

  .dark-theme .sticky-header {
    background: #2d2d2d;
    border-bottom-color: #444;
    box-shadow: 0 2px 4px rgba(0,0,0,0.3);
  }

  /* Full width containers */
  .full-width {
    width: 100% !important;
    box-sizing: border-box;
  }

  /* Ensure all containers take full width */
  * {
    box-sizing: border-box;
  }

  body {
    margin: 0;
    padding: 0;
    width: 100vw;
    overflow: hidden;
    transition: all 0.3s ease;
  }

  #root {
    width: 100%;
    margin: 0;
    padding: 0;
  }

  /* Theme toggle button */
  .theme-toggle {
    position: fixed;
    top: 20px;
    right: 20px;
    z-index: 1000;
    background: #4CAF50;
    border: none;
    border-radius: 50%;
    width: 50px;
    height: 50px;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 20px;
    box-shadow: 0 2px 10px rgba(0,0,0,0.2);
    transition: all 0.3s ease;
  }

  .theme-toggle:hover {
    transform: scale(1.1);
    box-shadow: 0 4px 15px rgba(0,0,0,0.3);
  }

  /* Dark theme specific styles */
  .dark-theme .panel-light {
    background: #2d2d2d !important;
    border-color: #444 !important;
    color: #ffffff !important;
  }

  .dark-theme .panel-info {
    background: #1e3a5f !important;
    border-color: #2d4d7a !important;
    color: #ffffff !important;
  }

  .dark-theme .panel-success {
    background: #1a472a !important;
    border-color: #2d5a3a !important;
    color: #ffffff !important;
  }

  .dark-theme .panel-warning {
    background: #5c4a1e !important;
    border-color: #7a6128 !important;
    color: #ffffff !important;
  }

  .dark-theme .panel-danger {
    background: #5c1a1a !important;
    border-color: #7a2828 !important;
    color: #ffffff !important;
  }

  .dark-theme .table-light {
    background: #2d2d2d !important;
    color: #ffffff !important;
  }

  .dark-theme .table-light th {
    background: #3d3d3d !important;
    border-color: #555 !important;
    color: #ffffff !important;
  }

  .dark-theme .table-light td {
    border-color: #555 !important;
    background: #2d2d2d !important;
  }

  .dark-theme .table-light tr:hover {
    background: #3d3d3d !important;
  }

  /* Button styles for dark theme */
  .dark-theme .btn-primary {
    background: #4CAF50 !important;
    border-color: #4CAF50 !important;
  }

  .dark-theme .btn-secondary {
    background: #6c757d !important;
    border-color: #6c757d !important;
  }

  .dark-theme .btn-info {
    background: #17a2b8 !important;
    border-color: #17a2b8 !important;
  }

  .dark-theme .btn-warning {
    background: #ff9800 !important;
    border-color: #ff9800 !important;
  }

  .dark-theme .btn-danger {
    background: #f44336 !important;
    border-color: #f44336 !important;
  }

  /* Input styles for dark theme */
  .dark-theme input,
  .dark-theme select {
    background: #2d2d2d !important;
    border-color: #555 !important;
    color: #ffffff !important;
  }

  .dark-theme input:focus,
  .dark-theme select:focus {
    border-color: #4CAF50 !important;
    box-shadow: 0 0 0 2px rgba(76, 175, 80, 0.2) !important;
  }

  .dark-theme input::placeholder {
    color: #999 !important;
  }

  /* Status badges */
  .status-badge {
    display: inline-block;
    padding: 4px 8px;
    border-radius: 12px;
    font-size: 12px;
    font-weight: bold;
    margin: 2px;
  }

  .status-present {
    background: #d4edda;
    color: #155724;
  }

  .status-late {
    background: #fff3cd;
    color: #856404;
  }

  .status-halfday {
    background: #cce5ff;
    color: #004085;
  }

  .status-absent {
    background: #f8d7da;
    color: #721c24;
  }

  .status-onleave {
    background: #e2e3e5;
    color: #383d41;
  }

  .dark-theme .status-present {
    background: #1a472a;
    color: #ffffff;
  }

  .dark-theme .status-late {
    background: #5c4a1e;
    color: #ffffff;
  }

  .dark-theme .status-halfday {
    background: #1e3a5f;
    color: #ffffff;
  }

  .dark-theme .status-absent {
    background: #5c1a1a;
    color: #ffffff;
  }

  .dark-theme .status-onleave {
    background: #3d3d3d;
    color: #ffffff;
  }

  /* Compliance violation indicator */
  .violation-indicator {
    position: absolute;
    top: -5px;
    right: -5px;
    width: 10px;
    height: 10px;
    background: #f44336;
    border-radius: 50%;
    animation: pulse 2s infinite;
  }

  @keyframes pulse {
    0% { transform: scale(0.95); opacity: 0.7; }
    50% { transform: scale(1.1); opacity: 1; }
    100% { transform: scale(0.95); opacity: 0.7; }
  }

  /* Shift color bars */
  .shift-bar {
    position: absolute;
    left: 0;
    top: 0;
    height: 100%;
    width: 4px;
  }

  /* Department color coding */
  .dept-development { border-left: 4px solid #4CAF50 !important; }
  .dept-testing { border-left: 4px solid #FF9800 !important; }
  .dept-devops { border-left: 4px solid #2196F3 !important; }
  .dept-support { border-left: 4px solid #9C27B0 !important; }
  .dept-management { border-left: 4px solid #F44336 !important; }
  .dept-default { border-left: 4px solid #607D8B !important; }

  /* Shift background colors */
  .shift-morning {
    background: linear-gradient(135deg, #4CAF50 0%, #C8E6C9 100%) !important;
    border-left: 4px solid #2E7D32 !important;
  }

  .shift-afternoon {
    background: linear-gradient(135deg, #FF9800 0%, #FFE0B2 100%) !important;
    border-left: 4px solid #EF6C00 !important;
  }

  .shift-night {
    background: linear-gradient(135deg, #F44336 0%, #FFCDD2 100%) !important;
    border-left: 4px solid #C62828 !important;
  }

  /* Leave event styling */
  .leave-event {
    background: linear-gradient(135deg, #6c757d 0%, #e9ecef 100%) !important;
    border: 2px dashed #495057 !important;
    opacity: 0.9 !important;
    color: #ffffff !important;
    font-weight: bold !important;
    position: relative !important;
  }
   .leave-event .shift-bar {
     position: absolute;
     left: 0;
     top: 0;
     bottom: 0;
     width: 4px;
     z-index: 1;
   }
    .dark-theme .leave-event {
    background: linear-gradient(135deg, #495057 0%, #6c757d 100%) !important;
    border: 2px dashed #343a40 !important;
  }

  /* OT Request Status Badges */
  .ot-status-pending {
    background: #fff3cd;
    color: #856404;
    padding: 4px 8px;
    border-radius: 4px;
    font-size: 12px;
    font-weight: bold;
  }

  .ot-status-approved {
    background: #d4edda;
    color: #155724;
    padding: 4px 8px;
    border-radius: 4px;
    font-size: 12px;
    font-weight: bold;
  }

  .ot-status-rejected {
    background: #f8d7da;
    color: #721c24;
    padding: 4px 8px;
    border-radius: 4px;
    font-size: 12px;
    font-weight: bold;
  }

  .dark-theme .ot-status-pending {
    background: #5c4a1e;
    color: #ffffff;
  }

  .dark-theme .ot-status-approved {
    background: #1a472a;
    color: #ffffff;
  }

  .dark-theme .ot-status-rejected {
    background: #5c1a1a;
    color: #ffffff;
  }

  /* OT Coverage Status Badges */
  .coverage-status-pending {
    background: #fff3cd;
    color: #856404;
    padding: 4px 8px;
    border-radius: 4px;
    font-size: 12px;
    font-weight: bold;
  }

  .coverage-status-assigned {
    background: #d1ecf1;
    color: #0c5460;
    padding: 4px 8px;
    border-radius: 4px;
    font-size: 12px;
    font-weight: bold;
  }

  .coverage-status-completed {
    background: #d4edda;
    color: #155724;
    padding: 4px 8px;
    border-radius: 4px;
    font-size: 12px;
    font-weight: bold;
  }

  .coverage-status-cancelled {
    background: #f8d7da;
    color: #721c24;
    padding: 4px 8px;
    border-radius: 4px;
    font-size: 12px;
    font-weight: bold;
  }

  .dark-theme .coverage-status-pending {
    background: #5c4a1e;
    color: #ffffff;
  }

  .dark-theme .coverage-status-assigned {
    background: #1e3a5f;
    color: #ffffff;
  }

  .dark-theme .coverage-status-completed {
    background: #1a472a;
    color: #ffffff;
  }

  .dark-theme .coverage-status-cancelled {
    background: #5c1a1a;
    color: #ffffff;
  }

  /* Back button styling */
  .back-button {
    padding: 8px 16px;
    background: #6c757d;
    color: white;
    border: none;
    border-radius: 4px;
    cursor: pointer;
    font-weight: bold;
    margin-right: 10px;
    display: flex;
    align-items: center;
    gap: 5px;
    transition: all 0.3s ease;
  }

  .back-button:hover {
    background: #5a6268;
    transform: translateX(-3px);
  }

  /* Notification badge */
  .notification-badge {
    position: relative;
    display: inline-block;
  }

  .notification-count {
    position: absolute;
    top: -5px;
    right: -5px;
    background: #f44336;
    color: white;
    border-radius: 50%;
    width: 18px;
    height: 18px;
    font-size: 11px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-weight: bold;
  }

  /* OT Coverage event styling */
  .ot-coverage-event {
    background: linear-gradient(135deg, #FFC107 0%, #FFF3CD 100%) !important;
    border: 2px solid #FF9800 !important;
    border-left: 6px solid #FF9800 !important;
    opacity: 0.95 !important;
    color: #000000 !important;
    font-weight: bold !important;
    position: relative !important;
    box-shadow: 0 2px 4px rgba(255, 152, 0, 0.3) !important;
  }

  .dark-theme .ot-coverage-event {
    background: linear-gradient(135deg, #FF9800 0%, #5c4a1e 100%) !important;
    border: 2px solid #FF9800 !important;
    color: #ffffff !important;
  }

  /* Loading spinner */
  .loading-spinner {
    border: 3px solid #f3f3f3;
    border-top: 3px solid #4CAF50;
    border-radius: 50%;
    width: 40px;
    height: 40px;
    animation: spin 1s linear infinite;
    margin: 20px auto;
  }

  @keyframes spin {
    0% { transform: rotate(0deg); }
    100% { transform: rotate(360deg); }
  }
  /* Enhanced Leave Event Styling */
  .leave-event {
      position: relative !important;
      overflow: hidden !important;
  }

  .leave-event::before {
      content: '';
      position: absolute;
      left: 0;
      top: 0;
      bottom: 0;
      width: 6px;
      background: var(--leave-color, #607D8B);
      z-index: 1;
  }

  .leave-event.leave-annual::before {
      background: #4CAF50 !important;
  }

  .leave-event.leave-sick::before {
      background: #2196F3 !important;
  }

  .leave-event.leave-casual::before {
      background: #FF9800 !important;
  }

  .leave-event .fc-event-content {
      position: relative;
      z-index: 2;
      padding-left: 12px !important;
  }

  /* Leave event hover effect */
  .leave-event:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(0,0,0,0.15);
      transition: all 0.3s ease;
  }

  /* Leave event tooltip */
  .leave-event-tooltip {
      position: absolute;
      background: rgba(0,0,0,0.9);
      color: white;
      padding: 10px;
      border-radius: 6px;
      font-size: 12px;
      z-index: 1000;
      max-width: 200px;
      pointer-events: none;
  }

  /* Department color bars in scheduler */
  .dept-color-bar {
      position: absolute;
      left: 0;
      top: 0;
      bottom: 0;
      width: 4px;
      z-index: 1;
  }

  .shift-event {
      position: relative;
      padding-left: 8px !important;
  }

  .shift-event .dept-color-bar {
      width: 4px;
      background: var(--dept-color, #607D8B);
  }

  /* Shift color bars */
  .shift-bar {
      position: absolute;
      left: 0;
      top: 0;
      height: 100%;
      width: 4px;
      z-index: 1;
  }
  /* Shift on leave - hidden color bar */
  .shift-on-leave {
      position: relative;
      opacity: 0.3 !important;
      border-left: 0px solid transparent !important;
      pointer-events: none; /* Disable interactions */
  }

  .shift-on-leave::after {
      content: '';
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background: repeating-linear-gradient(
          45deg,
          transparent,
          transparent 5px,
          rgba(0,0,0,0.1) 5px,
          rgba(0,0,0,0.1) 10px
      );
      pointer-events: none;
      opacity: 0.5;
      z-index: 1;
  }

  /* Ensure leave events appear above shift events */
  .leave-event {
      z-index: 100 !important;
      opacity: 0.9 !important;
  }

  .shift-event {
      z-index: 1 !important;
  }

  /* Hide tooltip for shifts on leave */
  .shift-on-leave .fc-event-tooltip {
      display: none !important;
  }

  /* Show different cursor for shifts on leave */
  .shift-on-leave {
      cursor: not-allowed !important;
  }

  /* Normal shift cursor */
  .shift-event:not(.shift-on-leave) {
      cursor: move !important;
  }
  .ot-coverage-event {
    background: linear-gradient(135deg, #FFC107 0%, #FFE57F 100%) !important;
    border: 2px solid #FF9800 !important;
    border-left: 6px solid #FF9800 !important;
    color: #000000 !important;
    font-weight: bold !important;
    position: relative !important;
    box-shadow: 0 2px 8px rgba(255, 152, 0, 0.3) !important;
    z-index: 50 !important;
  }

  .dark-theme .ot-coverage-event {
    background: linear-gradient(135deg, #FF9800 0%, #FFB74D 100%) !important;
    color: #ffffff !important;
  }

  .ot-coverage-event .shift-bar {
    position: absolute;
    left: 0;
    top: 0;
    bottom: 0;
    width: 6px;
    background: #FF9800;
    z-index: 1;
  }

  .ot-coverage-event:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(255, 152, 0, 0.5) !important;
    transition: all 0.3s ease;
  }

  /* Skill match badges */
  .skill-match-badge {
    display: inline-block;
    padding: 2px 8px;
    border-radius: 10px;
    font-size: 11px;
    font-weight: bold;
    margin: 2px;
  }

  .skill-match-high {
    background: #4CAF50;
    color: white;
  }

  .skill-match-medium {
    background: #FF9800;
    color: white;
  }

  .skill-match-low {
    background: #F44336;
    color: white;
  }

  /* FullCalendar specific styles */
  .fc {
    font-family: Arial, sans-serif !important;
  }

  .fc-theme-standard {
    background-color: transparent !important;
  }

  .fc-theme-standard td, .fc-theme-standard th {
    border-color: #ddd !important;
  }

  .dark-theme .fc-theme-standard td, .dark-theme .fc-theme-standard th {
    border-color: #444 !important;
    background-color: #2d2d2d !important;
  }

  .dark-theme .fc-col-header-cell {
    background-color: #3d3d3d !important;
    color: #ffffff !important;
  }

  .dark-theme .fc-daygrid-day {
    background-color: #2d2d2d !important;
  }

  .dark-theme .fc-timegrid-slot {
    background-color: #2d2d2d !important;
  }

  .dark-theme .fc-scrollgrid {
    border-color: #444 !important;
  }

  .dark-theme .fc-timegrid-axis {
    background-color: #3d3d3d !important;
    color: #ffffff !important;
  }

  .dark-theme .fc-timegrid-slot-label {
    color: #ffffff !important;
  }

  .dark-theme .fc-timegrid-col {
    background-color: #2d2d2d !important;
  }

  /* Resource column styling */
  .fc-resource-cell {
    font-weight: bold !important;
    padding: 8px !important;
  }

  .dark-theme .fc-resource-cell {
    background-color: #3d3d3d !important;
    color: #ffffff !important;
  }

  /* Event styling */
  .fc-event {
    border: none !important;
    border-radius: 4px !important;
    margin: 2px !important;
    cursor: pointer !important;
    transition: all 0.3s ease !important;
  }

  .fc-event:hover {
    transform: translateY(-2px) !important;
    box-shadow: 0 4px 8px rgba(0,0,0,0.2) !important;
  }

  .fc-event-title {
    font-weight: bold !important;
    padding: 2px 4px !important;
  }

  .fc-event-time {
    font-size: 12px !important;
    opacity: 0.8 !important;
  }

  /* Tooltip styling */
  .fc-event-tooltip {
    background: rgba(0,0,0,0.9) !important;
    color: white !important;
    padding: 10px !important;
    border-radius: 6px !important;
    font-size: 12px !important;
    max-width: 300px !important;
    z-index: 10000 !important;
  }

  /* Button styling in calendar */
  .fc-button {
    background-color: #4CAF50 !important;
    border-color: #4CAF50 !important;
    color: white !important;
    font-weight: bold !important;
  }

  .fc-button:hover {
    background-color: #45a049 !important;
    border-color: #45a049 !important;
  }

  .fc-button-primary:not(:disabled).fc-button-active {
    background-color: #2E7D32 !important;
    border-color: #2E7D32 !important;
  }

  .dark-theme .fc-button {
    background-color: #4CAF50 !important;
    border-color: #4CAF50 !important;
  }

  /* Today button styling */
  .fc .fc-button-primary:disabled {
    background-color: #6c757d !important;
    border-color: #6c757d !important;
    opacity: 0.6 !important;
  }

  /* Calendar header */
  .fc-toolbar-title {
    font-size: 1.5em !important;
    font-weight: bold !important;
    color: inherit !important;
  }

  .dark-theme .fc-toolbar-title {
    color: #ffffff !important;
  }

  /* Now indicator */
  .fc-timegrid-now-indicator-line {
    border-color: #f44336 !important;
  }

  .fc-timegrid-now-indicator-arrow {
    border-color: #f44336 !important;
  }

  /* Scrollbar in calendar */
  .fc-scroller {
    scrollbar-width: thin !important;
    scrollbar-color: #c1c1c1 #f1f1f1 !important;
  }

  .dark-theme .fc-scroller {
    scrollbar-color: #555 #2d2d2d !important;
  }

  .fc-scroller::-webkit-scrollbar {
    width: 8px !important;
    height: 8px !important;
  }

  .fc-scroller::-webkit-scrollbar-track {
    background: #f1f1f1 !important;
    border-radius: 4px !important;
  }

  .fc-scroller::-webkit-scrollbar-thumb {
    background: #c1c1c1 !important;
    border-radius: 4px !important;
  }

  .fc-scroller::-webkit-scrollbar-thumb:hover {
    background: #a8a8a8 !important;
  }

  .dark-theme .fc-scroller::-webkit-scrollbar-track {
    background: #2d2d2d !important;
  }

  .dark-theme .fc-scroller::-webkit-scrollbar-thumb {
    background: #555 !important;
  }

  .dark-theme .fc-scroller::-webkit-scrollbar-thumb:hover {
    background: #777 !important;
  }
  select {
      -webkit-appearance: menulist !important;
      -moz-appearance: menulist !important;
      appearance: menulist !important;
      z-index: 1000 !important;
      position: relative !important;
    }

    /* Prevent event bubbling for dropdowns */
    select:focus {
      outline: 2px solid #4CAF50 !important;
      outline-offset: 2px !important;
    }

    /* Ensure dropdowns appear above other content */
    .common-scrollbar-content select {
      z-index: 1000 !important;
    }

    /* Fix for dropdown in panels */
    .auto-height-panel select {
      z-index: 1000 !important;
      position: relative !important;
    }

    /* Prevent container from interfering with dropdowns */
    .common-scrollbar-content {
      position: relative;
    }

    /* Make sure dropdown options are visible */
    select option {
      position: static !important;
      background: white !important;
      color: black !important;
      z-index: 1001 !important;
    }

    .dark-theme select option {
      background: #2d2d2d !important;
      color: white !important;
    }
    .native-multi-select {
      appearance: none;           /* Remove default ugly styling */
      -webkit-appearance: none;
      -moz-appearance: none;
    }

    /* Custom arrow (optional - looks nicer) */
    .native-multi-select::-ms-expand {
      display: none;
    }

    .native-multi-select {
      background-image: url("data:image/svg+xml;charset=UTF-8,%3csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3e%3cpolygon points='0,0 12,0 6,12' fill='%23999'/%3e%3c/svg%3e");
      background-repeat: no-repeat;
      background-position: right 10px center;
      background-size: 12px;
      padding-right: 30px !important;
    }

    /* Hover/focus effect */
    .native-multi-select:focus {
      outline: none;
      box-shadow: 0 0 0 3px rgba(76, 175, 80, 0.3);
      border-color: #4CAF50;
    }

    /* Scrollbar styling (optional) */
    .native-multi-select::-webkit-scrollbar {
      width: 8px;
    }
    .native-multi-select::-webkit-scrollbar-track {
      background: #f1f1f1;
    }
    .native-multi-select::-webkit-scrollbar-thumb {
      background: #888;
      border-radius: 4px;
    }
    .native-multi-select::-webkit-scrollbar-thumb:hover {
      background: #555;
    }

    /* Dark mode adjustments */
    .dark-theme .native-multi-select {
      background-image: url("data:image/svg+xml;charset=UTF-8,%3csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3e%3cpolygon points='0,0 12,0 6,12' fill='%23ccc'/%3e%3c/svg%3e");
    }
/* Custom scrollbar for the multi-select */
.custom-multi-select {
  scrollbar-width: thin;                    /* Firefox */
  scrollbar-color: #888 #333;               /* Firefox */
}

/* WebKit browsers (Chrome, Safari, Edge) */
.custom-multi-select::-webkit-scrollbar {
  width: 8px;
}

.custom-multi-select::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 4px;
}

.custom-multi-select::-webkit-scrollbar-thumb {
  background: #888;
  border-radius: 4px;
}

.custom-multi-select::-webkit-scrollbar-thumb:hover {
  background: #555;
}

/* Dark mode scrollbar */
.dark-theme .custom-multi-select {
  scrollbar-color: #555 #222;
}

.dark-theme .custom-multi-select::-webkit-scrollbar-track {
  background: #222;
}

.dark-theme .custom-multi-select::-webkit-scrollbar-thumb {
  background: #555;
}

.dark-theme .custom-multi-select::-webkit-scrollbar-thumb:hover {
  background: #777;
}

/* Optional: Highlight selected options more clearly */
.custom-multi-select option:checked {
  background: linear-gradient(90deg, #4CAF50 0%, #388E3C 100%);
  color: white;
  font-weight: 500;
}
.custom-multi-select {
  overscroll-behavior: contain;
}
/* Better custom scrollbar for the dropdown */
.custom-multi-select::-webkit-scrollbar {
  width: 8px;
}

.custom-multi-select::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 4px;
  margin: 2px;
}

.custom-multi-select::-webkit-scrollbar-thumb {
  background: #888;
  border-radius: 4px;
  border: 2px solid #f1f1f1;
}

.custom-multi-select::-webkit-scrollbar-thumb:hover {
  background: #555;
}

.dark-theme .custom-multi-select::-webkit-scrollbar-track {
  background: #2d2d2d;
}

.dark-theme .custom-multi-select::-webkit-scrollbar-thumb {
  background: #666;
  border: 2px solid #2d2d2d;
}

/* Prevent parent scroll when dropdown is open */
body.dropdown-open {
  overflow: hidden;
}

/* Optional: visual feedback when dropdown is open */
[data-dropdown-open="true"] .fc {
  filter: brightness(0.85) blur(1px);
}
`;

// Inject global styles
const styleSheet = document.createElement('style');
styleSheet.innerText = globalStyles;
document.head.appendChild(styleSheet);

// Shift Configuration with precise times
const SHIFT_CONFIG = {
  Morning: {
    start: '09:00',
    end: '18:00',
    color: '#4CAF50',
    borderColor: '#2E7D32',
    bgColor: '#C8E6C9',
    gradient: 'linear-gradient(135deg, #4CAF50 0%, #C8E6C9 100%)'
  },
  Afternoon: {
    start: '13:00',
    end: '21:00',
    color: '#FF9800',
    borderColor: '#EF6C00',
    bgColor: '#FFE0B2',
    gradient: 'linear-gradient(135deg, #FF9800 0%, #FFE0B2 100%)'
  },
  Night: {
    start: '21:00',
    end: '06:00',
    color: '#F44336',
    borderColor: '#C62828',
    bgColor: '#FFCDD2',
    gradient: 'linear-gradient(135deg, #F44336 0%, #FFCDD2 100%)'
  }
};

// Department Colors
const DEPARTMENT_COLORS = {
  'Development': { color: '#4CAF50', name: 'Development' },
  'Testing': { color: '#FF9800', name: 'Testing' },
  'DevOps': { color: '#2196F3', name: 'DevOps' },
  'Support': { color: '#9C27B0', name: 'Support' },
  'Management': { color: '#F44336', name: 'Management' },
  'HR': { color: '#607D8B', name: 'HR' },
  'Finance': { color: '#795548', name: 'Finance' },
  'Sales': { color: '#009688', name: 'Sales' }
};

// OT Coverage Color
const OT_COVERAGE_COLOR = '#FFC107';

// Helper function to shuffle array randomly
const shuffleArray = (array) => {
  const newArray = [...array];
  for (let i = newArray.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [newArray[i], newArray[j]] = [newArray[j], newArray[i]];
  }
  return newArray;
};

// Helper function to get current day and date
const getCurrentDayAndDate = () => {
  const now = new Date();
  const options = {
    weekday: 'long',
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  };
  return now.toLocaleDateString('en-US', options);
};

// Helper function to create local timestamp
const createLocalTimestamp = () => {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  const hours = String(now.getHours()).padStart(2, '0');
  const minutes = String(now.getMinutes()).padStart(2, '0');
  const seconds = String(now.getSeconds()).padStart(2, '0');

  return `${year}-${month}-${day}T${hours}:${minutes}:${seconds}`;
};

// Helper function to get employee's scheduled shift for today
const getEmployeeShiftForToday = (employeeId, scheduleData) => {
  if (!scheduleData || !scheduleData.slots) return null;

  const today = new Date().toISOString().split('T')[0];
  const todaySlots = scheduleData.slots.filter(slot => slot.date === today);

  for (const slot of todaySlots) {
    if (slot.employees && Array.isArray(slot.employees)) {
      const employeeInSlot = slot.employees.find(emp => emp.id === employeeId);
      if (employeeInSlot) {
        return {
          shiftName: slot.name,
          shiftStart: SHIFT_CONFIG[slot.name]?.start || '09:00',
          shiftEnd: SHIFT_CONFIG[slot.name]?.end || '18:00'
        };
      }
    }
  }

  return null;
};

// Helper function to check if current time is within shift window
const isWithinShiftWindow = (shift, currentTime = new Date()) => {
  if (!shift) return false;

  const currentHours = currentTime.getHours();
  const currentMinutes = currentTime.getMinutes();
  const currentTotalMinutes = currentHours * 60 + currentMinutes;

  const [shiftStartHours, shiftStartMinutes] = shift.shiftStart.split(':').map(Number);
  const [shiftEndHours, shiftEndMinutes] = shift.shiftEnd.split(':').map(Number);

  const shiftStartTotalMinutes = shiftStartHours * 60 + shiftStartMinutes;
  const shiftEndTotalMinutes = shiftEndHours * 60 + shiftEndMinutes;

  // Handle night shift (crosses midnight)
  if (shift.shiftName === 'Night') {
    return currentTotalMinutes >= shiftStartTotalMinutes || currentTotalMinutes <= shiftEndTotalMinutes;
  }

  // Regular shifts - allow 30 minutes before and after shift
  const bufferMinutes = 30;
  return currentTotalMinutes >= (shiftStartTotalMinutes - bufferMinutes) &&
         currentTotalMinutes <= (shiftEndTotalMinutes + bufferMinutes);
};

// Helper function to calculate lateness
const calculateLateness = (shift, clockInTime = new Date()) => {
  if (!shift) return 0;

  const clockInHours = clockInTime.getHours();
  const clockInMinutes = clockInTime.getMinutes();
  const clockInTotalMinutes = clockInHours * 60 + clockInMinutes;

  const [shiftStartHours, shiftStartMinutes] = shift.shiftStart.split(':').map(Number);
  const shiftStartTotalMinutes = shiftStartHours * 60 + shiftStartMinutes;

  // 5-minute grace period
  const gracePeriod = 5;
  const lateStart = shiftStartTotalMinutes + gracePeriod;

  if (clockInTotalMinutes > lateStart) {
    return clockInTotalMinutes - lateStart;
  }

  return 0;
};

// Helper to check if should show late status in UI
const shouldShowLateInUI = (lateMinutes) => {
  // Only show if more than 15 minutes late
  return lateMinutes > 15;
};

// Helper function to format minutes into hours and minutes
const formatLateTime = (minutes) => {
  if (minutes <= 0) return 'On Time';

  const hours = Math.floor(minutes / 60);
  const remainingMinutes = minutes % 60;

  if (hours > 0) {
    return `${hours}h ${remainingMinutes}m late`;
  } else {
    return `${remainingMinutes}m late`;
  }
};

// Helper function to check if employee is on leave today
const isEmployeeOnLeaveToday = (employeeId, scheduleData) => {
  if (!scheduleData || !scheduleData.leaves) return false;

  const today = new Date().toISOString().split('T')[0];
  return scheduleData.leaves.some(leave =>
    leave.employeeId === employeeId && leave.date === today
  );
};

// Helper function to get attendance status badge
const getAttendanceStatusBadge = (status) => {
  switch(status) {
    case 'PRESENT':
      return <span className="status-badge status-present">Present</span>;
    case 'LATE':
      return <span className="status-badge status-late">Late</span>;
    case 'HALF_DAY':
      return <span className="status-badge status-halfday">Half Day</span>;
    case 'ABSENT':
      return <span className="status-badge status-absent">Absent</span>;
    default:
      return <span className="status-badge status-absent">Unknown</span>;
  }
};

// Helper function to get department class
const getDepartmentClass = (department) => {
  if (!department) return 'dept-default';

  const deptKey = Object.keys(DEPARTMENT_COLORS).find(key =>
    department.toLowerCase().includes(key.toLowerCase())
  );

  return deptKey ? `dept-${deptKey.toLowerCase()}` : 'dept-default';
};

// Helper function to get current active shift based on time
const getCurrentActiveShift = () => {
  const now = new Date();
  const currentHour = now.getHours();

  if (currentHour >= 9 && currentHour < 17) return 'Morning';
  if (currentHour >= 13 && currentHour < 21) return 'Afternoon';
  if (currentHour >= 21 || currentHour < 5) return 'Night';

  return null;
};

// Helper function to get OT status badge
const getOTStatusBadge = (status) => {
  switch(status) {
    case 'PENDING':
      return <span className="ot-status-pending">Pending</span>;
    case 'APPROVED':
      return <span className="ot-status-approved">Approved</span>;
    case 'REJECTED':
      return <span className="ot-status-rejected">Rejected</span>;
    default:
      return <span className="ot-status-pending">Pending</span>;
  }
};

// Helper function to get Coverage status badge
const getCoverageStatusBadge = (status) => {
  switch(status) {
    case 'PENDING':
      return <span className="coverage-status-pending">Pending</span>;
    case 'ASSIGNED':
      return <span className="coverage-status-assigned">Assigned</span>;
    case 'COMPLETED':
      return <span className="coverage-status-completed">Completed</span>;
    case 'CANCELLED':
      return <span className="coverage-status-cancelled">Cancelled</span>;
    default:
      return <span className="coverage-status-pending">Pending</span>;
  }
};

// Helper function to get leave color based on type
const getLeaveColor = (leaveType) => {
  switch(leaveType) {
    case 'ANNUAL':
      return '#4CAF50'; // Green
    case 'SICK':
      return '#2196F3'; // Blue
    case 'CASUAL':
      return '#FF9800'; // Orange
    default:
      return '#607D8B'; // Grey
  }
};

// Helper function to get leave gradient based on type
const getLeaveGradient = (leaveType) => {
  switch(leaveType) {
    case 'ANNUAL':
      return 'linear-gradient(135deg, #4CAF50 0%, #C8E6C9 100%)';
    case 'SICK':
      return 'linear-gradient(135deg, #2196F3 0%, #BBDEFB 100%)';
    case 'CASUAL':
      return 'linear-gradient(135deg, #FF9800 0%, #FFE0B2 100%)';
    default:
      return 'linear-gradient(135deg, #607D8B 0%, #ECEFF1 100%)';
  }
};

// Helper function to get notification title
const getNotificationTitle = (type) => {
  switch(type) {
    case 'LEAVE_COVERAGE_REQUIRED':
      return '🔄 Leave Coverage Required';
    case 'COVERAGE_ASSIGNED':
      return '✅ Coverage Assigned';
    case 'OT_APPROVAL_REQUIRED':
      return '💰 OT Approval Required';
    case 'OT_APPROVED':
      return '✅ OT Approved';
    case 'LEAVE_APPLIED':
      return '🏖️ Leave Applied';
    case 'COMPLIANCE_VIOLATION':
      return '⚠️ Compliance Violation';
    case 'CLOCK_IN':
      return '⏰ Clock In';
    case 'CLOCK_OUT':
      return '⏰ Clock Out';
    default:
      return '📢 Notification';
  }
};

function App() {
  const calendarRef = useRef(null);
  const [isSolving, setIsSolving] = useState(false);
  const [employeeCount, setEmployeeCount] = useState(0);
  const [leaveCount, setLeaveCount] = useState(0);
  const [showLeavePanel, setShowLeavePanel] = useState(false);
  const [showManageLeavesPanel, setShowManageLeavesPanel] = useState(false);
  const [showConfigPanel, setShowConfigPanel] = useState(false);
  const [showCompliancePanel, setShowCompliancePanel] = useState(false);
  const [showOvertimePanel, setShowOvertimePanel] = useState(false);
  const [selectedEmployee, setSelectedEmployee] = useState('');
  const [leaveStartDate, setLeaveStartDate] = useState('');
  const [leaveEndDate, setLeaveEndDate] = useState('');
  const [leaveType, setLeaveType] = useState('ANNUAL');
  const [employees, setEmployees] = useState([]);
  const [currentLeaves, setCurrentLeaves] = useState([]);
  const [loadingLeaves, setLoadingLeaves] = useState(false);
  const [showTimeClock, setShowTimeClock] = useState(false);
  const [todayAttendance, setTodayAttendance] = useState([]);
  const [currentDateTime, setCurrentDateTime] = useState('');
  const [employeeClockStatus, setEmployeeClockStatus] = useState({});
  const [clockedInEmployees, setClockedInEmployees] = useState([]);
  const [scheduleData, setScheduleData] = useState(null);
  const [currentShiftInfo, setCurrentShiftInfo] = useState(null);
  const [timeClockSelectedEmployee, setTimeClockSelectedEmployee] = useState('');
  const [systemConfig, setSystemConfig] = useState(null);
  const [complianceViolations, setComplianceViolations] = useState([]);
  const [overtimeRecords, setOvertimeRecords] = useState([]);
  const [employeeDetails, setEmployeeDetails] = useState({});
  const [availableEmployees, setAvailableEmployees] = useState([]);
  const [currentActiveShift, setCurrentActiveShift] = useState('');
  const [darkMode, setDarkMode] = useState(() => {
    const saved = localStorage.getItem('darkMode');
    return saved ? JSON.parse(saved) : false;
  });
  const [showOTRequestPanel, setShowOTRequestPanel] = useState(false);
  const [otRequests, setOtRequests] = useState([]);
  const [loadingOtRequests, setLoadingOtRequests] = useState(false);
  const [selectedOTEmployee, setSelectedOTEmployee] = useState('');
  const [otRequestDate, setOTRequestDate] = useState('');
  const [otRequestHours, setOTRequestHours] = useState('');
  const [otRequestReason, setOTRequestReason] = useState('');
  const [otRequestType, setOTRequestType] = useState('NORMAL');
  const [showOTManagementPanel, setShowOTManagementPanel] = useState(false);
  const [showLeaveCoveragePanel, setShowLeaveCoveragePanel] = useState(false);
  const [leaveCoverageRequests, setLeaveCoverageRequests] = useState([]);
  const [loadingCoverageRequests, setLoadingCoverageRequests] = useState(false);
  const [coverageAssignments, setCoverageAssignments] = useState([]);
  const [selectedCoverageRequest, setSelectedCoverageRequest] = useState(null);
  const [suitableEmployees, setSuitableEmployees] = useState([]);
  const [selectedCoverageEmployee, setSelectedCoverageEmployee] = useState('');
  const [coverageHours, setCoverageHours] = useState('');
  const [notifications, setNotifications] = useState([]);
  const [unreadNotificationCount, setUnreadNotificationCount] = useState(0);
  const [showNotificationsPanel, setShowNotificationsPanel] = useState(false);
  const [employeeLeaves, setEmployeeLeaves] = useState(new Map());
  const [selectedOTHours, setSelectedOTHours] = useState('1');
  const [coverageType, setCoverageType] = useState('COVERAGE');
  const [employeeOTWages, setEmployeeOTWages] = useState({});
  const [absentEmployeeSkills, setAbsentEmployeeSkills] = useState([]);
  const [skillThreshold, setSkillThreshold] = useState(50);
  const [showDirectCoveragePanel, setShowDirectCoveragePanel] = useState(false);
  const [directCoverageEmployee, setDirectCoverageEmployee] = useState('');
  const [directCoverageDate, setDirectCoverageDate] = useState('');
  const [directSuitableEmployees, setDirectSuitableEmployees] = useState([]);
  const [directCoverageHours, setDirectCoverageHours] = useState('1');
  const [directCoverageType, setDirectCoverageType] = useState('COVERAGE');
  const [directSelectedEmployee, setDirectSelectedEmployee] = useState('');
  const [calendarEvents, setCalendarEvents] = useState([]);
  const [calendarResources, setCalendarResources] = useState([]);
  const [calendarView, setCalendarView] = useState('resourceTimelineWeek');
  const [showAssignmentPanel, setShowAssignmentPanel] = useState(false);
  const [weekData, setWeekData] = useState(null);
  const [loadingWeekData, setLoadingWeekData] = useState(true);

  // Manager ID
  const managerId = 'MANAGER001';

  // Toggle dark mode
  const toggleDarkMode = () => {
    const newDarkMode = !darkMode;
    setDarkMode(newDarkMode);
    localStorage.setItem('darkMode', JSON.stringify(newDarkMode));
  };
const openAssignmentPanel = () => {
    setShowAssignmentPanel(true);
  };

const AssignmentPanel = React.memo(() => {
  const [weekDataState, setWeekDataState] = useState(weekData);
  const [selectedAssignments, setSelectedAssignments] = useState({});
  const [loading, setLoading] = useState(false);
  const panelRef = useRef(null);

  // Initialize selected assignments from week data
  useEffect(() => {
    const handleGlobalSubmit = (e) => {
      if (showAssignmentPanel) {
        e.preventDefault();
        e.stopPropagation();
        return false;
      }
    };

    document.addEventListener('submit', handleGlobalSubmit, true);

    return () => {
      document.removeEventListener('submit', handleGlobalSubmit, true);
    };
  }, [showAssignmentPanel]);
  useEffect(() => {
    if (weekDataState) {
      const initialAssignments = {};
      weekDataState.days?.forEach(day => {
        initialAssignments[day] = {};
        ['Morning', 'Afternoon', 'Night'].forEach(shift => {
          initialAssignments[day][shift] = weekDataState.assignments[day]?.[shift] || [];
        });
      });
      setSelectedAssignments(initialAssignments);
    }
  }, [weekDataState]);

  // Prevent any form submission in the panel
  useEffect(() => {
    const handleSubmit = (e) => {
      e.preventDefault();
      e.stopPropagation();
      return false;
    };

    const panel = panelRef.current;
    if (panel) {
      const forms = panel.querySelectorAll('form');
      forms.forEach(form => {
        form.addEventListener('submit', handleSubmit);
      });

      // Also prevent default on all buttons
      const buttons = panel.querySelectorAll('button');
      buttons.forEach(button => {
        if (button.type === 'submit') {
          button.type = 'button';
        }
      });
    }

    return () => {
      if (panel) {
        const forms = panel.querySelectorAll('form');
        forms.forEach(form => {
          form.removeEventListener('submit', handleSubmit);
        });
      }
    };
  }, []);

  // Handle toggle employee
  const handleEmployeeToggle = (day, shift, employeeId) => {
    setSelectedAssignments(prev => {
      const current = prev[day]?.[shift] || [];
      const newAssignments = [...current];

      if (newAssignments.includes(employeeId)) {
        // Remove employee
        const index = newAssignments.indexOf(employeeId);
        newAssignments.splice(index, 1);
      } else {
        // Add employee
        newAssignments.push(employeeId);
      }

      return {
        ...prev,
        [day]: {
          ...prev[day],
          [shift]: newAssignments
        }
      };
    });
  };

  // Save assignments
  const handleSave = async () => {
    setLoading(true);
    try {
      for (const day of weekDataState.days) {
        for (const shift of ['Morning', 'Afternoon', 'Night']) {
          const employeeIds = selectedAssignments[day]?.[shift] || [];
          if (employeeIds.length > 0) {
            await fetch('http://localhost:8083/shifts/assign', {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ day, shift, employees: employeeIds })
            });
          }
        }
      }

      alert('✅ Assignments saved!');

      // Refresh data
      const res = await fetch('http://localhost:8083/shifts');
      if (res.ok) {
        const newData = await res.json();
        setWeekDataState(newData);
        setWeekData(newData);
      }

      await loadShiftsAndAssignments(true);

    } catch (err) {
      alert('Error: ' + err.message);
    } finally {
      setLoading(false);
    }
  };

  if (!weekDataState) {
    return (
      <div style={{
        position: 'fixed',
        top: '50%',
        left: '50%',
        transform: 'translate(-50%, -50%)',
        background: darkMode ? '#1a1a1a' : 'white',
        padding: '40px',
        borderRadius: '12px',
        boxShadow: '0 10px 30px rgba(0,0,0,0.5)',
        zIndex: 1000,
        textAlign: 'center'
      }}>
        <div className="loading-spinner" style={{ margin: '0 auto 20px' }}></div>
        <div>Loading...</div>
      </div>
    );
  }

  const { days, employees } = weekDataState;

  return (
    <>
      {/* Backdrop - prevent click propagation */}
      <div
        style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          background: 'rgba(0, 0, 0, 0.6)',
          zIndex: 999,
          backdropFilter: 'blur(4px)',
        }}
        onClick={(e) => {
          e.preventDefault();
          e.stopPropagation();
          setShowAssignmentPanel(false);
        }}
      />

      {/* Main Panel */}
      <div
        ref={panelRef}
        style={{
          position: 'fixed',
          top: '50%',
          left: '50%',
          transform: 'translate(-50%, -50%)',
          width: '95%',
          maxWidth: '1400px',
          height: '90vh',
          background: darkMode ? '#1a1a1a' : 'white',
          borderRadius: '12px',
          boxShadow: '0 20px 50px rgba(0,0,0,0.8)',
          zIndex: 1000,
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden'
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div style={{
          padding: '20px',
          backgroundColor: darkMode ? '#2d2d2d' : '#f8f9fa',
          borderBottom: `1px solid ${darkMode ? '#444' : '#e9ecef'}`,
          flexShrink: 0
        }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <h2 style={{ margin: 0, color: darkMode ? '#ffffff' : '#333' }}>
              🔧 Manual Shift Assignment
            </h2>
            <button
              onClick={() => setShowAssignmentPanel(false)}
              style={{
                padding: '8px 16px',
                background: '#f44336',
                color: 'white',
                border: 'none',
                borderRadius: '6px',
                cursor: 'pointer',
                fontWeight: 'bold'
              }}
            >
              ✕ Close
            </button>
          </div>
        </div>

        {/* Main Content - Single scrollable area */}
        <div style={{
          flex: 1,
          overflowY: 'auto',
          padding: '20px'
        }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '25px' }}>
            {days.map((day, dayIndex) => {
              const dayName = new Date(day).toLocaleDateString('en-US', {
                weekday: 'long',
                month: 'long',
                day: 'numeric'
              });

              return (
                <div key={day} style={{
                  border: `1px solid ${darkMode ? '#444' : '#ddd'}`,
                  borderRadius: '8px',
                  padding: '20px',
                  background: darkMode ? '#2d2d2d' : '#ffffff'
                }}>
                  <h3 style={{
                    margin: '0 0 15px 0',
                    color: darkMode ? '#ffffff' : '#333',
                    borderBottom: `2px solid ${darkMode ? '#444' : '#eee'}`,
                    paddingBottom: '10px'
                  }}>
                    {dayName}
                  </h3>

                  <div style={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(3, 1fr)',
                    gap: '15px'
                  }}>
                    {['Morning', 'Afternoon', 'Night'].map((shift) => {
                      const shiftConfig = SHIFT_CONFIG[shift] || { color: '#607D8B' };
                      const assignedEmployees = selectedAssignments[day]?.[shift] || [];

                      return (
                        <div key={shift} style={{
                          padding: '15px',
                          background: darkMode ? '#3d3d3d' : '#f9f9f9',
                          borderRadius: '6px',
                          borderLeft: `4px solid ${shiftConfig.color}`
                        }}>
                          <div style={{
                            display: 'flex',
                            justifyContent: 'space-between',
                            alignItems: 'center',
                            marginBottom: '10px'
                          }}>
                            <span style={{ fontWeight: 'bold' }}>{shift}</span>
                            <span style={{
                              fontSize: '14px',
                              color: assignedEmployees.length > 0 ? '#4CAF50' : '#999',
                              fontWeight: 'bold'
                            }}>
                              {assignedEmployees.length} assigned
                            </span>
                          </div>

                          {/* Employee list with fixed height and scroll */}
                          <div style={{
                            height: '200px',
                            overflowY: 'auto',
                            border: `1px solid ${darkMode ? '#555' : '#ddd'}`,
                            borderRadius: '4px',
                            padding: '10px',
                            background: darkMode ? '#2d2d2d' : '#fff'
                          }}>
                            {employees
                              .filter(emp => !(shift === 'Night' && emp.gender?.toLowerCase() === 'female'))
                              .map(emp => {
                                const isAssigned = assignedEmployees.includes(emp.id);

                                return (
                                  <div
                                    key={emp.id}
                                    onClick={() => handleEmployeeToggle(day, shift, emp.id)}
                                    style={{
                                      padding: '8px',
                                      marginBottom: '6px',
                                      background: isAssigned
                                        ? (darkMode ? '#1a472a' : '#d4edda')
                                        : 'transparent',
                                      border: `1px solid ${isAssigned ? '#4CAF50' : 'transparent'}`,
                                      borderRadius: '4px',
                                      cursor: 'pointer',
                                      display: 'flex',
                                      alignItems: 'center',
                                      gap: '10px'
                                    }}
                                  >
                                    <div style={{
                                      width: '20px',
                                      height: '20px',
                                      border: `2px solid ${isAssigned ? '#4CAF50' : '#ccc'}`,
                                      borderRadius: '3px',
                                      background: isAssigned ? '#4CAF50' : 'transparent',
                                      display: 'flex',
                                      alignItems: 'center',
                                      justifyContent: 'center'
                                    }}>
                                      {isAssigned && <span style={{ color: 'white', fontSize: '12px' }}>✓</span>}
                                    </div>
                                    <div style={{ flex: 1 }}>
                                      <div style={{ fontWeight: 'bold', fontSize: '13px' }}>
                                        {emp.name}
                                      </div>
                                      <div style={{ fontSize: '11px', color: darkMode ? '#aaa' : '#666' }}>
                                        {emp.department} • {emp.rating || 3}⭐
                                      </div>
                                    </div>
                                  </div>
                                );
                              })}
                          </div>

                          {assignedEmployees.length > 0 && (
                            <div style={{
                              marginTop: '10px',
                              fontSize: '12px',
                              color: '#4CAF50',
                              fontWeight: 'bold'
                            }}>
                              Assigned: {assignedEmployees.slice(0, 3).map(id => {
                                const emp = employees.find(e => e.id === id);
                                return emp ? emp.name.split(' ')[0] : '';
                              }).join(', ')}
                              {assignedEmployees.length > 3 && ` +${assignedEmployees.length - 3} more`}
                            </div>
                          )}
                        </div>
                      );
                    })}
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* Footer */}
        <div style={{
          padding: '15px 20px',
          background: darkMode ? '#2d2d2d' : '#f8f9fa',
          borderTop: `1px solid ${darkMode ? '#444' : '#e9ecef'}`,
          flexShrink: 0
        }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div style={{ fontSize: '14px', color: darkMode ? '#aaa' : '#666' }}>
              Click employees to select/deselect
            </div>
            <div style={{ display: 'flex', gap: '10px' }}>
              <button
                onClick={handleSave}
                disabled={loading}
                style={{
                  padding: '10px 20px',
                  background: loading ? '#6c757d' : '#4CAF50',
                  color: 'white',
                  border: 'none',
                  borderRadius: '6px',
                  cursor: loading ? 'not-allowed' : 'pointer',
                  fontWeight: 'bold',
                  minWidth: '150px'
                }}
              >
                {loading ? 'Saving...' : '💾 Save Assignments'}
              </button>
            </div>
          </div>
        </div>
      </div>
    </>
  );
});

AssignmentPanel.displayName = 'AssignmentPanel';
  // Load employee details
  const loadEmployeeDetails = async () => {
    try {
      const res = await fetch('http://localhost:8083/employees');
      if (res.ok) {
        const data = await res.json();
        const employeeMap = {};
        data.forEach(emp => {
          employeeMap[emp.id] = {
            id: emp.id,
            name: emp.name,
            category: emp.category,
            gender: emp.gender,
            department: emp.department,
            position: emp.position,
            shiftColor: emp.shiftColor,
            email: emp.email,
            phone: emp.phone,
            hourlyWage: emp.hourlyWage,
            managerId: emp.managerId,
            annualLeaveBalance: emp.annualLeaveBalance,
            sickLeaveBalance: emp.sickLeaveBalance,
            casualLeaveBalance: emp.casualLeaveBalance,
            skills: emp.skills || [],
            performanceRating: emp.performanceRating || 3
          };
        });
        setEmployeeDetails(employeeMap);
        console.log('Employee details loaded:', Object.keys(employeeMap).length + ' employees');
      }
    } catch (err) {
      console.error('Failed to load employee details:', err);
    }
  };

  // Load system configuration
  const loadSystemConfig = async () => {
    try {
      const res = await fetch('http://localhost:8083/config');
      if (res.ok) {
        const data = await res.json();
        setSystemConfig(data);
        console.log('System config loaded:', data);
      }
    } catch (err) {
      console.error('Failed to load system config:', err);
    }
  };

  // Update system configuration
  const updateSystemConfig = async () => {
    try {
      const res = await fetch('http://localhost:8083/config', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(systemConfig)
      });

      if (res.ok) {
        alert('✅ System configuration updated successfully!');
        await loadSystemConfig();
      } else {
        alert('❌ Failed to update configuration');
      }
    } catch (err) {
      console.error('Failed to update config:', err);
      alert('Error updating configuration: ' + err.message);
    }
  };

  // Load compliance violations
  const loadComplianceViolations = async () => {
    try {
      const res = await fetch('http://localhost:8083/compliance/violations');
      if (res.ok) {
        const data = await res.json();
        setComplianceViolations(data.violations || []);
        console.log('Compliance violations loaded:', data);
      }
    } catch (err) {
      console.error('Failed to load compliance violations:', err);
    }
  };

  // Load overtime records
  const loadOvertimeRecords = async (employeeId = null) => {
    try {
      const url = employeeId ?
        `http://localhost:8083/overtime/${employeeId}` :
        'http://localhost:8083/overtime/requests';
      const res = await fetch(url);
      if (res.ok) {
        const data = await res.json();
        if (employeeId) {
          setOvertimeRecords(data.records || []);
        } else {
          setOtRequests(data.requests || []);
        }
      }
    } catch (err) {
      console.error('Failed to load overtime records:', err);
    }
  };

  const loadDirectCoverageOptions = async (employeeId, date) => {
    if (!employeeId || !date) {
      alert('Please select employee and date');
      return;
    }

    try {
      console.log(`🔍 Checking coverage options for ${employeeId} on ${date}`);

      // First, verify the employee is actually on leave on this date
      const isOnLeave = employeeLeaves.has(employeeId) &&
                       employeeLeaves.get(employeeId).has(date);

      if (!isOnLeave) {
        alert(`❌ Employee is not on leave on ${date}. Please select a valid leave date.`);
        return;
      }

      // Try the existing endpoint
      const res = await fetch(`http://localhost:8083/shifts/coverage/suitable-employees/${employeeId}/${date}`);

      if (!res.ok) {
        // If the endpoint doesn't exist, try the alternative endpoint
        console.log('Primary endpoint failed, trying alternative...');

        // Get suitable employees using the coverage request method
        const empDetails = employeeDetails[employeeId];
        if (!empDetails) {
          throw new Error('Employee details not found');
        }

        // Create a mock coverage request to get suitable employees
        const suitableEmployees = [];

        for (const emp of Object.values(employeeDetails)) {
          if (emp.id === employeeId) continue;

          // Basic suitability check
          if (emp.department === empDetails.department) {
            // Calculate skill similarity
            const commonSkills = emp.skills?.filter(skill =>
              empDetails.skills?.includes(skill)
            ) || [];

            const skillMatch = commonSkills.length > 0 ?
              (commonSkills.length / Math.max(emp.skills?.length || 1, empDetails.skills?.length || 1)) * 100 : 0;

            suitableEmployees.push({
              ...emp,
              skillMatch: `${skillMatch.toFixed(0)}%`,
              otWages: {
                '1h': calculateOTWage(emp.id, 1, 'COVERAGE'),
                '2h': calculateOTWage(emp.id, 2, 'COVERAGE'),
                '3h': calculateOTWage(emp.id, 3, 'COVERAGE')
              }
            });
          }
        }

        // Sort by skill match
        suitableEmployees.sort((a, b) => {
          const aScore = parseFloat(a.skillMatch);
          const bScore = parseFloat(b.skillMatch);
          return bScore - aScore;
        });

        setDirectSuitableEmployees(suitableEmployees);

        if (suitableEmployees.length === 0) {
          alert('No suitable employees found for coverage');
        }

        return;
      }

      const data = await res.json();
      console.log('Direct coverage options:', data);

      setDirectSuitableEmployees(data.suitableEmployees || []);

      if (data.suitableEmployees.length === 0) {
        alert('No suitable employees found for coverage');
      }

    } catch (err) {
      console.error('Failed to load direct coverage options:', err);
      alert('Error: ' + err.message);
    }
  };

  const loadEmployeeLeavesMap = async () => {
    try {
      const res = await fetch('http://localhost:8083/shifts/all-leaves-detailed');
      if (res.ok) {
        const data = await res.json();
        const leavesMap = new Map();

        data.leaves?.forEach(leave => {
          if (!leavesMap.has(leave.employeeId)) {
            leavesMap.set(leave.employeeId, new Set());
          }
          leavesMap.get(leave.employeeId).add(leave.leaveDate);
        });

        setEmployeeLeaves(leavesMap);
        console.log('Loaded employee leaves map:', leavesMap.size + ' employees');
      }
    } catch (err) {
      console.error('Failed to load employee leaves map:', err);
    }
  };

  // Function for quick coverage assignment
  const quickAssignCoverage = async () => {
    if (!directCoverageEmployee || !directCoverageDate || !directSelectedEmployee) {
      alert('Please select employee, date, and covering employee');
      return;
    }

    try {
      const res = await fetch('http://localhost:8083/shifts/coverage/quick-assign', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          employeeId: directCoverageEmployee,
          date: directCoverageDate,
          assignedEmployeeId: directSelectedEmployee,
          hours: parseFloat(directCoverageHours),
          managerId: managerId,
          coverageType: directCoverageType
        })
      });

      const data = await res.json();

      if (res.ok) {
        alert(`✅ Coverage assigned successfully!\n\n` +
              `Covering: ${data.assignmentDetails?.assignedEmployeeName}\n` +
              `For: ${data.assignmentDetails?.coveredEmployeeName}\n` +
              `Date: ${directCoverageDate}\n` +
              `Hours: ${directCoverageHours}h\n` +
              `OT Pay: ${data.formattedWage}`);

        // Reset form
        setDirectCoverageEmployee('');
        setDirectCoverageDate('');
        setDirectSelectedEmployee('');
        setDirectCoverageHours('1');
        setDirectCoverageType('COVERAGE');
        setDirectSuitableEmployees([]);
        setShowDirectCoveragePanel(false);

        // Refresh data
        await loadLeaveCoverageRequests();
        await loadCoverageAssignments();
        await loadNotifications();
        await loadShiftsAndAssignments();

      } else {
        alert('❌ Failed to assign coverage: ' + (data.error || 'Unknown error'));
      }

    } catch (err) {
      console.error('Quick coverage assignment error:', err);
      alert('Error: ' + err.message);
    }
  };

  // Load available employees for current shift
  const loadAvailableEmployees = async () => {
    try {
      const res = await fetch('http://localhost:8083/shifts/available-clockin');
      if (res.ok) {
        const data = await res.json();
        setAvailableEmployees(data.availableEmployees || []);
        console.log('Available employees:', data);
      }
    } catch (err) {
      console.error('Failed to load available employees:', err);
      setAvailableEmployees([]);
    }
  };

  // Load OT requests
  const loadOTRequests = async () => {
    setLoadingOtRequests(true);
    try {
      const res = await fetch('http://localhost:8083/overtime/requests');
      if (res.ok) {
        const data = await res.json();
        setOtRequests(data.requests || []);
        console.log('OT requests loaded:', data);
      }
    } catch (err) {
      console.error('Failed to load OT requests:', err);
      setOtRequests([]);
    } finally {
      setLoadingOtRequests(false);
    }
  };

  // Load Leave Coverage Requests
  const loadLeaveCoverageRequests = async () => {
    setLoadingCoverageRequests(true);
    try {
      const res = await fetch(`http://localhost:8083/overtime/coverage-requests?managerId=${managerId}`);
      if (res.ok) {
        const data = await res.json();
        setLeaveCoverageRequests(data.requests || []);
        console.log('Leave coverage requests loaded:', data);
      }
    } catch (err) {
      console.error('Failed to load coverage requests:', err);
      setLeaveCoverageRequests([]);
    } finally {
      setLoadingCoverageRequests(false);
    }
  };

  // Load Coverage Assignments
  const loadCoverageAssignments = async () => {
    try {
      const res = await fetch('http://localhost:8083/overtime/coverage-assignments');
      if (res.ok) {
        const data = await res.json();
        setCoverageAssignments(data.assignments || []);
        console.log('Coverage assignments loaded:', data);
      }
    } catch (err) {
      console.error('Failed to load coverage assignments:', err);
      setCoverageAssignments([]);
    }
  };

  // Load Notifications
  const loadNotifications = async () => {
    try {
      const res = await fetch(`http://localhost:8083/notifications/${managerId}`);
      if (res.ok) {
        const data = await res.json();
        setNotifications(data.notifications || []);
        const unread = data.notifications.filter(n => !n.read).length;
        setUnreadNotificationCount(unread);
//         console.log('Notifications loaded:', data);
      }
    } catch (err) {
      console.error('Failed to load notifications:', err);
      setNotifications([]);
    }
  };

  // Mark notification as read
  const markNotificationAsRead = async (notificationId) => {
    try {
      const res = await fetch(`http://localhost:8083/notifications/${notificationId}/read`, {
        method: 'POST'
      });
      if (res.ok) {
        await loadNotifications();
      }
    } catch (err) {
      console.error('Failed to mark notification as read:', err);
    }
  };

  // Mark all notifications as read
  const markAllNotificationsAsRead = async () => {
    try {
      const unreadNotifications = notifications.filter(n => !n.read);
      for (const notification of unreadNotifications) {
        await markNotificationAsRead(notification.id);
      }
    } catch (err) {
      console.error('Failed to mark all notifications as read:', err);
    }
  };

  // Get suitable employees for coverage
  const loadSuitableEmployees = async (requestId, skillThreshold = 50) => {
    try {
      console.log(`🔍 Loading suitable employees with ${skillThreshold}% skill threshold...`);

      const res = await fetch(
        `http://localhost:8083/overtime/coverage-requests/${requestId}/suitable-employees?skillThreshold=${skillThreshold}`
      );

      if (!res.ok) {
        console.error('Failed to fetch coverage request:', res.status, res.statusText);
        setSuitableEmployees([]);
        return;
      }

      const data = await res.json();
      console.log('📋 Coverage request response:', data);

      if (data.suitableEmployees && data.suitableEmployees.length === 0) {
        console.log('⚠️ No suitable employees found. Try lowering skill threshold.');

        // Try with lower threshold
        if (skillThreshold > 30) {
          console.log('🔄 Trying with lower skill threshold (30%)...');
          return await loadSuitableEmployees(requestId, 30);
        }
      }

      // Map the backend data to match frontend employee format
      const mappedEmployees = data.suitableEmployees.map(emp => {
        const employeeInfo = {
          id: emp.id,
          name: emp.name,
          department: emp.department,
          position: emp.position,
          gender: emp.gender || 'Male',
          category: emp.category || 'Regular',
          skills: emp.skills || [],
          shiftColor: emp.shiftColor || '#607D8B',
          hourlyWage: emp.hourlyWage || 0,
          email: emp.email || '',
          phone: emp.phone || '',
          skillScore: emp.skillScore || 0,
          totalScore: emp.totalScore || 0,
          sameDepartment: emp.sameDepartment || false,
          nightShiftCertified: emp.nightShiftCertified || false,
          suitabilityLevel: emp.suitabilityLevel || 'UNKNOWN',
          weeklyOTHours: emp.weeklyOTHours || 0
        };

        // Store OT wages for this employee
        if (emp.otWages) {
          setEmployeeOTWages(prev => ({
            ...prev,
            [emp.id]: emp.otWages
          }));
        }

        console.log(`Mapped employee ${employeeInfo.name}:`, {
          skillMatch: `${employeeInfo.skillScore.toFixed(1)}%`,
          suitability: employeeInfo.suitabilityLevel,
          skills: employeeInfo.skills.length
        });

        return employeeInfo;
      }).filter(emp => emp.id);

      console.log(`✅ Found ${mappedEmployees.length} suitable employees with ${skillThreshold}% threshold`);

      if (mappedEmployees.length === 0 && data.absentEmployee) {
        console.log('DEBUG: Absent employee info:', data.absentEmployee);

        // Show debug info
        alert(`No suitable employees found with ${skillThreshold}% skill threshold.\n\n` +
              `Absent employee: ${data.absentEmployee.name}\n` +
              `Department: ${data.absentEmployee.department}\n` +
              `Required skills: ${data.absentEmployee.skills?.join(', ') || 'None'}\n\n` +
              `Try lowering the skill threshold or check if employees have matching skills.`);
      }

      setSuitableEmployees(mappedEmployees);

    } catch (err) {
      console.error('❌ Failed to load suitable employees:', err);
      console.error('Error stack:', err.stack);
      setSuitableEmployees([]);
    }
  };

  // Assign coverage to employee
  const assignCoverage = async (requestId, employeeId, hours, type) => {
    if (!employeeId || !hours) {
      alert('Please select employee and OT hours');
      return;
    }

    try {
      const res = await fetch(`http://localhost:8083/overtime/coverage-requests/${requestId}/assign-with-wage`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          assignedEmployeeId: employeeId,
          hours: parseFloat(hours),
          managerId: managerId,
          coverageType: type || 'COVERAGE'
        })
      });

      const data = await res.json();

      if (res.ok) {
        alert(`✅ Coverage assigned successfully!\n\n` +
              `OT Hours: ${hours} hour(s)\n` +
              `Estimated OT Pay: ${data.estimatedPay || '$0.00'}\n` +
              `Coverage Type: ${type || 'COVERAGE'}`);

        // Reset form
        setSelectedCoverageEmployee('');
        setSelectedOTHours('1');
        setCoverageType('COVERAGE');

        // Reload data
        await loadLeaveCoverageRequests();
        await loadCoverageAssignments();
        await loadShiftsAndAssignments();

        // Reload notifications
        await loadNotifications();
      } else {
        alert('❌ Failed to assign coverage: ' + (data.error || 'Unknown error'));
      }
    } catch (err) {
      console.error('Assign coverage error:', err);
      alert('Error assigning coverage: ' + err.message);
    }
  };

  // Complete coverage
  const completeCoverage = async (requestId) => {
    try {
      const res = await fetch(`http://localhost:8083/overtime/coverage-requests/${requestId}/complete`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          managerId: managerId
        })
      });

      if (res.ok) {
        alert('✅ Coverage marked as completed!');
        await loadLeaveCoverageRequests();
        await loadCoverageAssignments();
        await loadShiftsAndAssignments();
      } else {
        alert('❌ Failed to complete coverage');
      }
    } catch (err) {
      console.error('Complete coverage error:', err);
      alert('Error completing coverage: ' + err.message);
    }
  };

  // Cancel coverage
  const cancelCoverage = async (requestId) => {
    if (!window.confirm('Are you sure you want to cancel this coverage request?')) {
      return;
    }

    try {
      const res = await fetch(`http://localhost:8083/overtime/coverage-requests/${requestId}/cancel`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          managerId: managerId
        })
      });

      if (res.ok) {
        alert('✅ Coverage request cancelled!');
        await loadLeaveCoverageRequests();
        await loadCoverageAssignments();
        await loadShiftsAndAssignments();
      } else {
        alert('❌ Failed to cancel coverage request');
      }
    } catch (err) {
      console.error('Cancel coverage error:', err);
      alert('Error cancelling coverage: ' + err.message);
    }
  };

  // Submit OT request
  const submitOTRequest = async () => {
    if (!selectedOTEmployee || !otRequestDate || !otRequestHours || !otRequestReason) {
      alert('Please fill all required fields');
      return;
    }

    try {
      const res = await fetch('http://localhost:8083/overtime/request', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          employeeId: selectedOTEmployee,
          date: otRequestDate,
          requestedHours: parseFloat(otRequestHours),
          reason: otRequestReason,
          type: otRequestType
        })
      });

      const data = await res.json();

      if (res.ok) {
        alert('✅ OT request submitted successfully!');
        // Reset form
        setSelectedOTEmployee('');
        setOTRequestDate('');
        setOTRequestHours('');
        setOTRequestReason('');
        setOTRequestType('NORMAL');
        setShowOTRequestPanel(false);

        // Reload OT requests
        await loadOTRequests();
        await loadNotifications();
        await loadShiftsAndAssignments();
      } else {
        alert('❌ Failed to submit OT request: ' + (data.error || 'Unknown error'));
      }
    } catch (err) {
      console.error('OT request error:', err);
      alert('Error submitting OT request: ' + err.message);
    }
  };

  // Approve OT request
  const approveOTRequest = async (requestId) => {
    try {
      const res = await fetch(`http://localhost:8083/overtime/requests/${requestId}/approve`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          managerId: managerId,
          notes: 'Approved by manager'
        })
      });

      const data = await res.json();

      if (res.ok) {
        alert('✅ OT request approved successfully!');
        await loadOTRequests();
        await loadNotifications();
        await loadShiftsAndAssignments();
      } else {
        alert('❌ Failed to approve OT request: ' + (data.error || 'Unknown error'));
      }
    } catch (err) {
      console.error('Approve OT request error:', err);
      alert('Error approving OT request: ' + err.message);
    }
  };

  // Reject OT request
  const rejectOTRequest = async (requestId) => {
    const rejectionReason = prompt('Enter rejection reason:');
    if (!rejectionReason) return;

    try {
      const res = await fetch(`http://localhost:8083/overtime/requests/${requestId}/reject`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          managerId: managerId,
          notes: rejectionReason
        })
      });

      const data = await res.json();

      if (res.ok) {
        alert('✅ OT request rejected successfully!');
        await loadOTRequests();
        await loadNotifications();
        await loadShiftsAndAssignments();
      } else {
        alert('❌ Failed to reject OT request: ' + (data.error || 'Unknown error'));
      }
    } catch (err) {
      console.error('Reject OT request error:', err);
      alert('Error rejecting OT request: ' + err.message);
    }
  };

  // Clock in for a specific employee
  const handleClockInForEmployee = async (employeeId) => {
    try {
      // First, verify employee's shift for today
      const shiftRes = await fetch(`http://localhost:8083/shifts/employee/${employeeId}/today`);
      const shiftData = await shiftRes.json();

      if (shiftData.onLeave) {
        alert('❌ This employee is on leave today and cannot clock in!');
        return;
      }

      if (!shiftData.hasShift) {
        alert('❌ This employee does not have any shift scheduled for today!');
        return;
      }

      if (!shiftData.canWorkShift) {
        alert('❌ This employee cannot work this shift due to restrictions!');
        return;
      }

      // Check if already clocked in
      if (employeeClockStatus[employeeId]) {
        alert('❌ This employee is already clocked in!');
        return;
      }

      // Check if current time is within shift window
      const currentTime = new Date();
      const [startHour, startMinute] = shiftData.startTime.split(':').map(Number);
      const [endHour, endMinute] = shiftData.endTime.split(':').map(Number);

      const shiftStart = new Date();
      shiftStart.setHours(startHour, startMinute, 0, 0);

      const shiftEnd = new Date();
      shiftEnd.setHours(endHour, endMinute, 0, 0);

      // Handle night shift
      if (endHour < startHour) {
        shiftEnd.setDate(shiftEnd.getDate() + 1);
      }

      // Allow 30 minutes buffer before shift
      const earliestClockIn = new Date(shiftStart.getTime() - 30 * 60000);

      if (currentTime < earliestClockIn) {
        alert('❌ Too early! Earliest clock-in is 30 minutes before shift.');
        return;
      }

      if (currentTime > shiftEnd) {
        alert('❌ Too late! Shift has already ended.');
        return;
      }

      // Proceed with clock-in
      const timestamp = createLocalTimestamp();

      const res = await fetch('http://localhost:8083/shifts/clock-in', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          employeeId: employeeId,
          timestamp: timestamp
        })
      });

      const data = await res.json();

      if (res.ok) {
        alert('✅ ' + data.employeeName + ' clocked in successfully!');
        loadClockStatus();
        loadTodayAttendance();
        loadAvailableEmployees();
        await loadComplianceViolations();
        await loadNotifications();
      } else {
        alert('Failed to clock in: ' + (data.error || 'Unknown error'));
      }
    } catch (err) {
      console.error('Clock in error:', err);
      alert('Error: ' + err.message);
    }
  };

  // Remove all leaves for an employee
  const removeAllLeaves = async (employeeId) => {
    if (!window.confirm('Are you sure you want to remove ALL leaves for this employee?')) {
      return;
    }

    try {
      const res = await fetch('http://localhost:8083/shifts/remove-leave', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          employeeId: employeeId
        })
      });

      const data = await res.json();

      if (res.ok) {
        alert(`✅ All leaves removed for employee!\nEmployee: ${data.employeeName}\nRemoved: ${data.removedCount} leaves`);
        const newEmployeeLeavesMap = new Map(employeeLeaves);
        newEmployeeLeavesMap.delete(employeeId);
        setEmployeeLeaves(newEmployeeLeavesMap);

        // Update states
        await loadEmployeeDetails();
        await loadCurrentLeaves();
        await loadNotifications();
        await loadShiftsAndAssignments();

      } else {
        alert('❌ Failed to remove leaves: ' + (data.error || 'Unknown error'));
      }
    } catch (err) {
      alert('Error: ' + err.message);
    }
  };

  // Update current date and time every second
  useEffect(() => {
    const updateDateTime = () => {
      setCurrentDateTime(getCurrentDayAndDate());
      setCurrentActiveShift(getCurrentActiveShift());

      // Update current shift information
      if (timeClockSelectedEmployee && scheduleData) {
        const isOnLeave = isEmployeeOnLeaveToday(timeClockSelectedEmployee, scheduleData);

        if (isOnLeave) {
          setCurrentShiftInfo(null);
        } else {
          const shift = getEmployeeShiftForToday(timeClockSelectedEmployee, scheduleData);
          setCurrentShiftInfo(shift);
        }
      } else {
        setCurrentShiftInfo(null);
      }
    };

    updateDateTime(); // Initial call
    const interval = setInterval(updateDateTime, 1000);

    return () => clearInterval(interval);
  }, [timeClockSelectedEmployee, scheduleData]);

  // Load clock status periodically when time clock is open
  useEffect(() => {
    let interval;
    if (showTimeClock) {
      loadClockStatus();
      loadAvailableEmployees();
      interval = setInterval(() => {
        loadClockStatus();
        loadAvailableEmployees();
      }, 3000);
    }
    return () => clearInterval(interval);
  }, [showTimeClock]);

  // Load OT requests when OT management panel is opened
  useEffect(() => {
    if (showOTManagementPanel) {
      loadOTRequests();
    }
  }, [showOTManagementPanel]);

  // Load coverage requests when coverage panel is opened
  useEffect(() => {
    if (showLeaveCoveragePanel) {
      loadLeaveCoverageRequests();
      loadCoverageAssignments();
    }
  }, [showLeaveCoveragePanel]);

  // Load notifications periodically
  useEffect(() => {
    loadNotifications();
    const interval = setInterval(() => {
      loadNotifications();
    }, 10000); // Refresh every 10 seconds

    return () => clearInterval(interval);
  }, []);

  // Apply dark mode to body
  useEffect(() => {
    document.body.className = darkMode ? 'dark-theme' : 'light-theme';
  }, [darkMode]);

  // Load initial data
  useEffect(() => {
    loadWeekData();
    loadSystemConfig();
    loadEmployeeDetails();
    loadComplianceViolations();
    loadCoverageAssignments();
    loadEmployeeLeavesMap();
    setCurrentActiveShift(getCurrentActiveShift());

    // Set today's date for OT request form
    const today = new Date().toISOString().split('T')[0];
    setOTRequestDate(today);

    // Load initial schedule data
    loadShiftsAndAssignments();
   }, []);
    const loadWeekData = async () => {
      try {
        setLoadingWeekData(true);
        const res = await fetch('http://localhost:8083/shifts');
        if (res.ok) {
          const data = await res.json();
          setWeekData(data);
          console.log('Week data loaded:', data);
        }
      } catch (err) {
        console.error('Failed to load week data:', err);
      } finally {
        setLoadingWeekData(false);
      }
    };
      // Load shifts and assignments for FullCalendar
      const loadShiftsAndAssignments = async (forceRefresh = false) => {
       try {
        console.log('🔍 Loading shifts data...', forceRefresh ? '(force refresh)' : '');

        // Always fetch fresh data from backend
        const endpoint = forceRefresh ? 'http://localhost:8083/shifts?refresh=' + Date.now() : 'http://localhost:8083/shifts';
        const res = await fetch(endpoint);

        if (!res.ok) {
            throw new Error(`HTTP ${res.status}: ${res.statusText}`);
        }

        const data = await res.json();
        console.log('📊 Backend data received:', {
            employeesCount: data.employees?.length || 0,
            slotsCount: data.slots?.length || 0,
            leavesCount: data.leaves?.length || 0
        });

        // IMPORTANT: Update state IMMEDIATELY
        setScheduleData(data);
        setEmployeeCount(data.employees?.length || 0);
        setLeaveCount(data.leaves?.length || 0);

        // Process for calendar
        const employeeMap = new Map();
        data.employees?.forEach(assignment => {
            if (!employeeMap.has(assignment.id)) {
                employeeMap.set(assignment.id, {
                    id: assignment.id,
                    title: assignment.name,
                    department: assignment.department || 'Unknown',
                    position: assignment.position || 'Employee',
                    shiftColor: assignment.employeeColor || '#607D8B'
                });
            }
        });

        const resources = Array.from(employeeMap.values());
        setEmployees(resources);

        // Format calendar resources
        const calendarResources = resources.map(resource => ({
            id: resource.id,
            title: resource.title,
            department: resource.department,
            position: resource.position
        }));
        setCalendarResources(calendarResources);

        // Build events
        const events = [];

        // Add shift events
        if (data.slots && Array.isArray(data.slots)) {
            data.slots.forEach(slot => {
                const shiftConfig = SHIFT_CONFIG[slot.name];
                if (!shiftConfig) return;

                const start = new Date(slot.date + 'T' + shiftConfig.start);
                let end = new Date(slot.date + 'T' + shiftConfig.end);

                if (slot.name === 'Night') {
                    end.setDate(end.getDate() + 1);
                }

                if (slot.employees && Array.isArray(slot.employees)) {
                    slot.employees.forEach(emp => {
                        events.push({
                            id: `shift-${emp.id}-${slot.date}-${slot.name}`,
                            resourceId: emp.id,
                            title: slot.name,
                            start: start,
                            end: end,
                            className: `shift-${slot.name.toLowerCase()}`,
                            backgroundColor: shiftConfig.color,
                            borderColor: shiftConfig.borderColor,
                            textColor: 'white',
                            extendedProps: {
                                type: 'shift',
                                shiftName: slot.name,
                                employeeId: emp.id,
                                employeeName: emp.name || emp.id,
                                department: emp.department
                            }
                        });
                    });
                }
            });
        }

        // Add leave events
        if (data.leaves && Array.isArray(data.leaves)) {
            data.leaves.forEach(leave => {
                const leaveDate = new Date(leave.leaveDate + 'T09:00:00');
                const leaveEnd = new Date(leave.leaveDate + 'T18:00:00');

                events.push({
                    id: `leave-${leave.employeeId}-${leave.leaveDate}`,
                    resourceId: leave.employeeId,
                    title: 'On Leave',
                    start: leaveDate,
                    end: leaveEnd,
                    className: 'leave-event',
                    backgroundColor: '#6c757d',
                    borderColor: '#495057',
                    textColor: 'white',
                    extendedProps: {
                        type: 'leave',
                        employeeId: leave.employeeId,
                        employeeName: leave.employeeName
                    }
                });
            });
        }

        console.log(`🎯 Loading ${events.length} events into calendar`);

        // CRITICAL: Force calendar update
        setCalendarEvents(events);

        // Force calendar to re-render
        if (calendarRef.current) {
          setTimeout(() => {
            const calendarApi = calendarRef.current.getApi();
            if (calendarApi) {  // ← ADD THIS LINE
              calendarApi.refetchEvents();
              calendarApi.render();
            }
          }, 100);
        }

    } catch (err) {
        console.error('❌ Failed to load shifts:', err);
        alert('Failed to load schedule: ' + err.message);
    }
  };

const reoptimize = async () => {
  if (isSolving) return;

  // Set loading state immediately
  setIsSolving(true);

  // Show immediate feedback
  alert("🔄 Generating new optimized shifts...\n\nThis will take 10-20 seconds.\nNew performance ratings will be assigned to all employees!");

  try {
    console.log('🔄 Re-optimizing schedule...');

    // Step 1: Clear existing states first
    setEmployeeClockStatus({});
    setClockedInEmployees([]);
    setTimeClockSelectedEmployee('');
    setCurrentShiftInfo(null);
    setTodayAttendance([]);
    setAvailableEmployees([]);

    // ────────────────────────────────
    // NEW: Assign fresh random ratings to ALL employees
    // ────────────────────────────────
    const newRatings = {};
    Object.keys(employeeDetails).forEach(id => {
      // Random rating from 1 to 5
      const randomRating = Math.floor(Math.random() * 5) + 1;
      newRatings[id] = randomRating;
    });

    // Update employeeDetails state with new ratings
    setEmployeeDetails(prev => {
      const updated = { ...prev };
      Object.entries(newRatings).forEach(([id, rating]) => {
        if (updated[id]) {
          updated[id] = { ...updated[id], performanceRating: rating };
        }
      });
      return updated;
    });

    console.log('✨ Assigned new random performance ratings to all employees:', newRatings);
    // ───────────────────────────────────────

    // Step 2: Call backend optimization
    const response = await fetch('http://localhost:8083/shifts', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({})
    });

    if (!response.ok) {
      const errorText = await response.text();
      throw new Error('Optimization failed: ' + errorText);
    }

    const data = await response.json();
    console.log('✅ Optimized schedule received:', data);

    // Step 3: Force refresh ALL data
    await Promise.all([
      loadShiftsAndAssignments(true),
      loadEmployeeDetails(),
      loadSystemConfig(),
      loadAvailableEmployees(),
      loadClockStatus(),
      loadTodayAttendance()
    ]);

    // Step 4: Update local state with new data
    if (data.employees && data.slots) {
      setScheduleData({
        employees: data.employees,
        slots: data.slots,
        leaves: data.leaves || [],
        otCoverages: data.otCoverages || []
      });

      setEmployeeCount(data.employeeCount || 0);
      await loadShiftsAndAssignments(true); // Double refresh to ensure ratings show
    }

    console.log('✅ Calendar updated with re-optimized schedule and new ratings');

    // Step 5: Show success message
    const topPerformers = Object.values(newRatings).filter(r => r >= 4).length;
    const lowPerformers = Object.values(newRatings).filter(r => r <= 2).length;

    alert(`✅ Schedule re-optimized successfully!\n\n` +
          `• New shifts generated\n` +
          `• New performance ratings assigned\n` +
          `  → ${topPerformers} top performers (4–5 ⭐)\n` +
          `  → ${lowPerformers} need improvement (1–2 ⭐)\n\n` +
          `Scroll down to see updated schedule and ratings!`);

    // Step 6: Scroll to calendar
    setTimeout(() => {
      const calendarElement = document.querySelector('.fc');
      if (calendarElement) {
        calendarElement.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }
    }, 500);

  } catch (err) {
    console.error('❌ Reoptimize failed:', err);

    const errorMsg = err.message.includes('timeout')
      ? 'Optimization took too long. Try again or check server logs.'
      : 'Failed to generate shifts: ' + err.message;

    alert('❌ ' + errorMsg);

    await loadShiftsAndAssignments(false);
  } finally {
    setIsSolving(false);
    console.log('🏁 Optimization process completed');
  }
};

  const applyLeave = async () => {
    if (!selectedEmployee || !leaveStartDate || !leaveEndDate) {
      alert('Please select employee and date range');
      return;
    }

    try {
      const res = await fetch('http://localhost:8083/shifts/apply-leave', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          employeeId: selectedEmployee,
          startDate: leaveStartDate,
          endDate: leaveEndDate,
          leaveType: leaveType
        })
      });

      const data = await res.json();

      if (res.ok) {
        alert(`✅ Leave applied successfully!\nEmployee: ${data.employeeName}\nDays: ${data.leaveDays}\nType: ${data.leaveType}`);

        // Reset form
        setSelectedEmployee('');
        setLeaveStartDate('');
        setLeaveEndDate('');
        setLeaveType('ANNUAL');
        setShowLeavePanel(false);

        const newEmployeeLeavesMap = new Map(employeeLeaves);

        if (!newEmployeeLeavesMap.has(data.employeeId)) {
          newEmployeeLeavesMap.set(data.employeeId, new Set());
        }

        // Add all leave dates from the response
        if (data.leaveRecords && Array.isArray(data.leaveRecords)) {
          data.leaveRecords.forEach(record => {
            newEmployeeLeavesMap.get(data.employeeId).add(record.leaveDate);
          });
        }

        setEmployeeLeaves(newEmployeeLeavesMap);
        console.log('✅ Updated employeeLeaves Map after applying leave');

        // Refresh employee details and leaves
        await loadEmployeeDetails();
        await loadCurrentLeaves();
        await loadNotifications();
        await loadShiftsAndAssignments();

      } else {
        alert('❌ Failed to apply leave: ' + (data.error || 'Unknown error'));
      }
    } catch (err) {
      alert('Error: ' + err.message);
    }
  };

  const calculateOTWage = (employeeId, hours, type) => {
    const emp = employeeDetails[employeeId];
    if (!emp) return 0;

    let multiplier = 1.2; // Default coverage multiplier

    switch(type) {
      case 'EMERGENCY':
        multiplier = 1.5;
        break;
      case 'HOLIDAY':
        multiplier = 2.0;
        break;
      case 'COVERAGE':
      default:
        multiplier = 1.2;
        break;
    }

    return emp.hourlyWage * hours * multiplier;
  };

  const revokeLeave = async (employeeId, leaveDate, leaveType = 'ANNUAL') => {
    if (!window.confirm(`Are you sure you want to revoke leave on ${leaveDate}?`)) {
      return;
    }

    try {
      const res = await fetch('http://localhost:8083/shifts/revoke-leave', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          employeeId: employeeId,
          leaveDate: leaveDate,
          leaveType: leaveType
        })
      });

      const data = await res.json();

      if (res.ok) {
        alert(`✅ Leave revoked successfully!\nEmployee: ${data.employeeName}\nDate: ${data.leaveDate}`);
        const newEmployeeLeavesMap = new Map(employeeLeaves);
        if (newEmployeeLeavesMap.has(employeeId)) {
          newEmployeeLeavesMap.get(employeeId).delete(leaveDate);
          // Remove employee from map if no more leaves
          if (newEmployeeLeavesMap.get(employeeId).size === 0) {
            newEmployeeLeavesMap.delete(employeeId);
          }
          setEmployeeLeaves(newEmployeeLeavesMap);
        }
        // Update local state
        await loadEmployeeDetails();
        await loadCurrentLeaves();
        await loadNotifications();
        await loadShiftsAndAssignments();

      } else {
        alert('❌ Failed to revoke leave: ' + (data.error || 'Unknown error'));
      }
    } catch (err) {
      alert('Error: ' + err.message);
    }
  };

  const openManageLeavesPanel = async () => {
    setShowManageLeavesPanel(true);
    if (Object.keys(employeeDetails).length === 0) {
      await loadEmployeeDetails();
    }
    await loadCurrentLeaves();
  };

  const openLeaveCoveragePanel = async () => {
    setShowLeaveCoveragePanel(true);
    await loadLeaveCoverageRequests();
    await loadCoverageAssignments();
  };

  const loadClockStatus = async () => {
    try {
      const res = await fetch('http://localhost:8083/shifts/clock-status');
      if (res.ok) {
        const data = await res.json();
        setEmployeeClockStatus(data.clockStatus || {});
        setClockedInEmployees(data.clockedInEmployees || []);
      }
    } catch (err) {
      console.error('Failed to load clock status:', err);
    }
  };

  const getEmployeeClockStatus = (employeeId) => {
    return employeeClockStatus[employeeId] || false;
  };

  const handleClockIn = async () => {
    if (!timeClockSelectedEmployee) {
      alert('Please select an employee');
      return;
    }

    try {
      // Check if employee is on leave today
      const isOnLeave = isEmployeeOnLeaveToday(timeClockSelectedEmployee, scheduleData);

      if (isOnLeave) {
        alert('❌ This employee is on leave today and cannot clock in!');
        return;
      }

      // Check if employee has a shift scheduled for today
      const shift = getEmployeeShiftForToday(timeClockSelectedEmployee, scheduleData);

      if (!shift) {
        alert('❌ This employee does not have any shift scheduled for today!');
        return;
      }

      // Check if current time is within shift window
      const currentTime = new Date();
      if (!isWithinShiftWindow(shift, currentTime)) {
        const shiftTime = shift.shiftStart + ' - ' + shift.shiftEnd;
        alert('❌ Cannot clock in! ' + shift.shiftName + ' shift time is ' + shiftTime + '. Current time is ' + currentTime.toLocaleTimeString());
        return;
      }

      // Calculate lateness
      const lateMinutes = calculateLateness(shift, currentTime);
      const lateMessage = lateMinutes > 0 ? ' (' + formatLateTime(lateMinutes) + ')' : ' (On Time)';

      const timestamp = createLocalTimestamp();

      const res = await fetch('http://localhost:8083/shifts/clock-in', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          employeeId: timeClockSelectedEmployee,
          timestamp: timestamp
        })
      });

      const data = await res.json();

      if (res.ok) {
        const employeeName = employees.find(emp => emp.id === timeClockSelectedEmployee)?.title;
        alert('✅ ' + employeeName + ' clocked in successfully for ' + shift.shiftName + ' shift!' + lateMessage);
        setTimeClockSelectedEmployee('');
        loadClockStatus();
        loadTodayAttendance();
        loadAvailableEmployees();
        await loadComplianceViolations();
        await loadNotifications();
      } else {
        alert('Failed to clock in: ' + (data.error || 'Unknown error'));
      }
    } catch (err) {
      console.error('Clock in error:', err);
      alert('Error: ' + err.message);
    }
  };

  const handleClockOut = async (employeeId = null) => {
    const employeeToClockOut = employeeId || timeClockSelectedEmployee;

    if (!employeeToClockOut) {
      alert('Please select an employee');
      return;
    }

    try {
      const shift = getEmployeeShiftForToday(employeeToClockOut, scheduleData);

      if (!shift) {
        alert('❌ This employee does not have any shift scheduled for today!');
        return;
      }

      const timestamp = createLocalTimestamp();

      const res = await fetch('http://localhost:8083/shifts/clock-out', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          employeeId: employeeToClockOut,
          timestamp: timestamp
        })
      });

      const data = await res.json();

      if (res.ok) {
        const employeeName = employees.find(emp => emp.id === employeeToClockOut)?.title;
        alert('✅ ' + employeeName + ' clocked out successfully! Worked: ' + (data.hoursWorked || 0).toFixed(2) + ' hours');

        if (!employeeId) {
          setTimeClockSelectedEmployee('');
        }

        loadClockStatus();
        loadTodayAttendance();
        loadAvailableEmployees();
        await loadComplianceViolations();
        await loadOvertimeRecords(employeeToClockOut);
        await loadNotifications();
      } else {
        alert('Failed to clock out: ' + (data.error || 'Unknown error'));
      }
    } catch (err) {
      console.error('Clock out error:', err);
      alert('Error: ' + err.message);
    }
  };

  const handleBreakStart = async (employeeId) => {
    try {
      const timestamp = createLocalTimestamp();
      const res = await fetch('http://localhost:8083/shifts/break/start', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          employeeId: employeeId,
          timestamp: timestamp
        })
      });

      if (res.ok) {
        alert('Break started successfully!');
      } else {
        alert('Failed to start break');
      }
    } catch (err) {
      alert('Error: ' + err.message);
    }
  };

  const handleBreakEnd = async (employeeId) => {
    try {
      const timestamp = createLocalTimestamp();
      const res = await fetch('http://localhost:8083/shifts/break/end', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          employeeId: employeeId,
          timestamp: timestamp
        })
      });

      if (res.ok) {
        alert('Break ended successfully!');
      } else {
        alert('Failed to end break');
      }
    } catch (err) {
      alert('Error: ' + err.message);
    }
  };

  const loadTodayAttendance = async () => {
    try {
      const res = await fetch('http://localhost:8083/shifts/attendance/today');
      if (res.ok) {
        const data = await res.json();
        setTodayAttendance(data.records || []);
      } else {
        console.error('Failed to load attendance:', res.status);
        setTodayAttendance([]);
      }
    } catch (err) {
      console.error('Failed to load attendance:', err);
      setTodayAttendance([]);
    }
  };

  // Update useEffect for time clock
  useEffect(() => {
    if (showTimeClock) {
      loadTodayAttendance();
      loadClockStatus();
      loadAvailableEmployees();
    }
  }, [showTimeClock]);

  // Load current leaves for management
  const loadCurrentLeaves = async () => {
    setLoadingLeaves(true);
    try {
      console.log('🔄 Loading current leaves...');

      // Use the new detailed endpoint
      const res = await fetch('http://localhost:8083/shifts/all-leaves-detailed');

      if (!res.ok) {
        throw new Error(`HTTP error! status: ${res.status}`);
      }

      const data = await res.json();

      console.log('📊 Detailed leave data from backend:', {
        count: data.count,
        employeesWithLeaves: data.employeesWithLeaves,
        leaves: data.leaves?.length || 0
      });

      // Process leaves data
      const leavesArray = data.leaves || [];

      // Update local state
      setCurrentLeaves(leavesArray);
      setLeaveCount(data.count || 0);

      // ALSO POPULATE employeeLeaves Map
      const newEmployeeLeavesMap = new Map();
      leavesArray.forEach(leave => {
        if (!newEmployeeLeavesMap.has(leave.employeeId)) {
          newEmployeeLeavesMap.set(leave.employeeId, new Set());
        }
        newEmployeeLeavesMap.get(leave.employeeId).add(leave.leaveDate);
      });

      setEmployeeLeaves(newEmployeeLeavesMap);
      console.log('✅ Updated employeeLeaves Map with', newEmployeeLeavesMap.size, 'employees');

      console.log('✅ Successfully loaded', leavesArray.length, 'leave records');
      console.log('Sample leave record:', leavesArray[0]);

    } catch (err) {
      console.error('❌ Failed to load leaves:', err);
      console.error('Error details:', {
        message: err.message,
        stack: err.stack
      });

      setCurrentLeaves([]);
      setLeaveCount(0);
      setEmployeeLeaves(new Map()); // Clear the map on error
      alert('Failed to load leaves: ' + err.message);

    } finally {
      setLoadingLeaves(false);
      console.log('🏁 Load leaves completed');
    }
  };

  const resolveViolation = async (violationId) => {
    const resolutionNotes = prompt('Enter resolution notes:');
    if (!resolutionNotes) return;

    try {
      const res = await fetch('http://localhost:8083/compliance/violations/' + violationId + '/resolve', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ resolutionNotes })
      });

      if (res.ok) {
        alert('Violation resolved successfully!');
        await loadComplianceViolations();
      } else {
        alert('Failed to resolve violation');
      }
    } catch (err) {
      alert('Error: ' + err.message);
    }
  };

  // Style functions
  const getButtonStyle = (type, disabled = false) => {
    const baseStyle = {
      padding: '12px 24px',
      fontWeight: 'bold',
      fontSize: '16px',
      border: 'none',
      borderRadius: 6,
      cursor: 'pointer',
      marginRight: 15,
      marginBottom: 10,
      transition: 'all 0.3s ease'
    };

    if (disabled) {
      return {
        ...baseStyle,
        background: '#6c757d',
        color: '#ffffff',
        opacity: 0.6,
        cursor: 'not-allowed'
      };
    }

    const colors = {
      primary: { background: '#28a745', color: 'white' },
      info: { background: '#17a2b8', color: 'white' },
      secondary: { background: '#6f42c1', color: 'white' },
      warning: { background: '#ff9800', color: 'white' },
      danger: { background: '#f44336', color: 'white' },
      success: { background: '#4CAF50', color: 'white' }
    };

    return { ...baseStyle, ...colors[type] };
  };

  const getInputStyle = () => {
    return darkMode ? {
      background: '#2d2d2d',
      border: '1px solid #555',
      color: '#ffffff',
      padding: '8px 12px',
      borderRadius: 4,
      width: '100%'
    } : {
      padding: '8px 12px',
      borderRadius: 4,
      border: '1px solid #ccc',
      width: '100%'
    };
  };

  // Render config panel
  const renderConfigPanel = () => {
    if (!systemConfig) return null;

    return (
      <div className="auto-height-panel full-width" style={{
        marginBottom: 20,
        padding: 20,
        backgroundColor: darkMode ? '#1e3a5f' : '#e7f3ff',
        borderRadius: 8,
        border: '2px solid ' + (darkMode ? '#2d4d7a' : '#bee5eb'),
        color: darkMode ? '#ffffff' : '#0c5460'
      }}>
        <h3 style={{ marginTop: 0 }}>⚙️ System Configuration</h3>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))', gap: 20 }}>
          {/* Attendance Rules */}
          <div>
            <h4>Attendance Rules</h4>
            <div style={{ marginBottom: 10 }}>
              <label>Minimum Hours for Present:</label>
              <input
                type="number"
                value={systemConfig.minHoursForPresent || 4}
                onChange={(e) => setSystemConfig({...systemConfig, minHoursForPresent: parseInt(e.target.value)})}
                style={getInputStyle()}
              />
            </div>
            <div style={{ marginBottom: 10 }}>
              <label>Half Day Hours:</label>
              <input
                type="number"
                value={systemConfig.halfDayHours || 4}
                onChange={(e) => setSystemConfig({...systemConfig, halfDayHours: parseInt(e.target.value)})}
                style={getInputStyle()}
              />
            </div>
            <div style={{ marginBottom: 10 }}>
              <label>Grace Period (minutes):</label>
              <input
                type="number"
                value={systemConfig.gracePeriodMinutes || 5}
                onChange={(e) => setSystemConfig({...systemConfig, gracePeriodMinutes: parseInt(e.target.value)})}
                style={getInputStyle()}
              />
            </div>
            <div style={{ marginBottom: 10 }}>
              <label>Rounding Interval (minutes):</label>
              <input
                type="number"
                value={systemConfig.roundingIntervalMinutes || 5}
                onChange={(e) => setSystemConfig({...systemConfig, roundingIntervalMinutes: parseInt(e.target.value)})}
                style={getInputStyle()}
              />
            </div>
          </div>

          {/* Overtime Rules */}
          <div>
            <h4>Overtime Rules</h4>
            <div style={{ marginBottom: 10 }}>
              <label>Normal OT Rate (multiplier):</label>
              <input
                type="number"
                step="0.1"
                value={systemConfig.otRateNormal || 1.5}
                onChange={(e) => setSystemConfig({...systemConfig, otRateNormal: parseFloat(e.target.value)})}
                style={getInputStyle()}
              />
            </div>
            <div style={{ marginBottom: 10 }}>
              <label>Holiday OT Rate (multiplier):</label>
              <input
                type="number"
                step="0.1"
                value={systemConfig.otRateHoliday || 2.0}
                onChange={(e) => setSystemConfig({...systemConfig, otRateHoliday: parseFloat(e.target.value)})}
                style={getInputStyle()}
              />
            </div>
            <div style={{ marginBottom: 10 }}>
              <label>Max Daily OT Hours:</label>
              <input
                type="number"
                value={systemConfig.maxDailyOTHours || 3}
                onChange={(e) => setSystemConfig({...systemConfig, maxDailyOTHours: parseInt(e.target.value)})}
                style={getInputStyle()}
              />
            </div>
            <div style={{ marginBottom: 10 }}>
              <label>Auto Approve OT:</label>
              <input
                type="checkbox"
                checked={systemConfig.autoApproveOT || false}
                onChange={(e) => setSystemConfig({...systemConfig, autoApproveOT: e.target.checked})}
                style={{ marginLeft: 10 }}
              />
            </div>
            <div style={{ marginBottom: 10 }}>
              <label>Max Weekly OT Hours:</label>
              <input
                type="number"
                value={systemConfig.maxWeeklyOTHours || 15}
                onChange={(e) => setSystemConfig({...systemConfig, maxWeeklyOTHours: parseInt(e.target.value)})}
                style={getInputStyle()}
              />
            </div>
          </div>

          {/* Leave Coverage Rules */}
          <div>
            <h4>Leave Coverage Rules</h4>
            <div style={{ marginBottom: 10 }}>
              <label>Notify Leave Coverage Required:</label>
              <input
                type="checkbox"
                checked={systemConfig.notifyLeaveCoverageRequired !== false}
                onChange={(e) => setSystemConfig({...systemConfig, notifyLeaveCoverageRequired: e.target.checked})}
                style={{ marginLeft: 10 }}
              />
            </div>
            <div style={{ marginBottom: 10 }}>
              <label>Coverage OT Rate Multiplier:</label>
              <input
                type="number"
                step="0.1"
                value={systemConfig.otRateCoverage || 1.2}
                onChange={(e) => setSystemConfig({...systemConfig, otRateCoverage: parseFloat(e.target.value)})}
                style={getInputStyle()}
              />
            </div>
            <div style={{ marginBottom: 10 }}>
              <label>Auto Assign Coverage:</label>
              <input
                type="checkbox"
                checked={systemConfig.autoAssignCoverage || false}
                onChange={(e) => setSystemConfig({...systemConfig, autoAssignCoverage: e.target.checked})}
                style={{ marginLeft: 10 }}
              />
            </div>
          </div>

          {/* Shift Constraints */}
          <div>
            <h4>Shift Constraints</h4>
            <div style={{ marginBottom: 10 }}>
              <label>Max Consecutive Working Days:</label>
              <input
                type="number"
                value={systemConfig.maxConsecutiveWorkingDays || 6}
                onChange={(e) => setSystemConfig({...systemConfig, maxConsecutiveWorkingDays: parseInt(e.target.value)})}
                style={getInputStyle()}
              />
            </div>
            <div style={{ marginBottom: 10 }}>
              <label>Max Consecutive Night Shifts:</label>
              <input
                type="number"
                value={systemConfig.maxConsecutiveNightShifts || 3}
                onChange={(e) => setSystemConfig({...systemConfig, maxConsecutiveNightShifts: parseInt(e.target.value)})}
                style={getInputStyle()}
              />
            </div>
            <div style={{ marginBottom: 10 }}>
              <label>Min Gap Between Shifts (hours):</label>
              <input
                type="number"
                value={systemConfig.minGapBetweenShiftsHours || 11}
                onChange={(e) => setSystemConfig({...systemConfig, minGapBetweenShiftsHours: parseInt(e.target.value)})}
                style={getInputStyle()}
              />
            </div>
          </div>

          {/* Government Compliance */}
          <div>
            <h4>Government Compliance</h4>
            <div style={{ marginBottom: 10 }}>
              <label>Max Daily Hours (Law):</label>
              <input
                type="number"
                value={systemConfig.maxDailyHoursLaw || 12}
                onChange={(e) => setSystemConfig({...systemConfig, maxDailyHoursLaw: parseInt(e.target.value)})}
                style={getInputStyle()}
              />
            </div>
            <div style={{ marginBottom: 10 }}>
              <label>Max Weekly Hours (Law):</label>
              <input
                type="number"
                value={systemConfig.maxWeeklyHoursLaw || 48}
                onChange={(e) => setSystemConfig({...systemConfig, maxWeeklyHoursLaw: parseInt(e.target.value)})}
                style={getInputStyle()}
              />
            </div>
            <div style={{ marginBottom: 10 }}>
              <label>Minimum Hourly Wage:</label>
              <input
                type="number"
                value={systemConfig.minimumHourlyWage || 18}
                onChange={(e) => setSystemConfig({...systemConfig, minimumHourlyWage: parseInt(e.target.value)})}
                style={getInputStyle()}
              />
            </div>
            <div style={{ marginBottom: 10 }}>
              <label>Female Shift Restrictions:</label>
              <input
                type="checkbox"
                checked={systemConfig.femaleShiftRestrictions || true}
                onChange={(e) => setSystemConfig({...systemConfig, femaleShiftRestrictions: e.target.checked})}
                style={{ marginLeft: 10 }}
              />
            </div>
          </div>
        </div>

        <div style={{ marginTop: 20, textAlign: 'right' }}>
          <button
            onClick={updateSystemConfig}
            style={getButtonStyle('primary')}
          >
            💾 Save Configuration
          </button>
          <button
            onClick={() => setShowConfigPanel(false)}
            style={getButtonStyle('secondary')}
          >
            Cancel
          </button>
        </div>
      </div>
    );
  };

  // Render compliance panel
  const renderCompliancePanel = () => {
    const unresolvedViolations = complianceViolations.filter(v => !v.resolved);
    const resolvedViolations = complianceViolations.filter(v => v.resolved);

    return (
      <div className="auto-height-panel full-width" style={{
        marginBottom: 20,
        padding: 20,
        backgroundColor: darkMode ? '#5c1a1a' : '#f8d7da',
        borderRadius: 8,
        border: '2px solid ' + (darkMode ? '#7a2828' : '#f5c6cb'),
        color: darkMode ? '#ffffff' : '#721c24'
      }}>
        <h3 style={{ marginTop: 0 }}>⚠️ Compliance Violations</h3>

        {/* Summary */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))',
          gap: 10,
          marginBottom: 20
        }}>
          <div style={{
            padding: '15px',
            backgroundColor: darkMode ? '#3d3d3d' : '#ffffff',
            borderRadius: '6px',
            textAlign: 'center'
          }}>
            <div style={{ fontSize: '24px', fontWeight: 'bold', color: '#f44336' }}>
              {unresolvedViolations.length}
            </div>
            <div>Unresolved</div>
          </div>
          <div style={{
            padding: '15px',
            backgroundColor: darkMode ? '#3d3d3d' : '#ffffff',
            borderRadius: '6px',
            textAlign: 'center'
          }}>
            <div style={{ fontSize: '24px', fontWeight: 'bold', color: '#4CAF50' }}>
              {resolvedViolations.length}
            </div>
            <div>Resolved</div>
          </div>
          <div style={{
            padding: '15px',
            backgroundColor: darkMode ? '#3d3d3d' : '#ffffff',
            borderRadius: '6px',
            textAlign: 'center'
          }}>
            <div style={{ fontSize: '24px', fontWeight: 'bold', color: '#FF9800' }}>
              {complianceViolations.length}
            </div>
            <div>Total</div>
          </div>
        </div>

        {/* Violations List */}
        <div className="no-individual-scroll full-width">
          <h4>Unresolved Violations</h4>
          {unresolvedViolations.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '20px', color: darkMode ? '#cccccc' : '#666' }}>
              No unresolved violations
            </div>
          ) : (
            <table style={{
              width: '100%',
              borderCollapse: 'collapse',
              backgroundColor: darkMode ? '#2d2d2d' : '#ffffff',
              color: darkMode ? '#ffffff' : 'inherit'
            }}>
              <thead>
                <tr style={{ backgroundColor: darkMode ? '#3d3d3d' : '#f8f9fa' }}>
                  <th style={{ padding: '10px', border: '1px solid ' + (darkMode ? '#555' : '#dee2e6'), textAlign: 'left' }}>Employee</th>
                  <th style={{ padding: '10px', border: '1px solid ' + (darkMode ? '#555' : '#dee2e6'), textAlign: 'left' }}>Type</th>
                  <th style={{ padding: '10px', border: '1px solid ' + (darkMode ? '#555' : '#dee2e6'), textAlign: 'left' }}>Description</th>
                  <th style={{ padding: '10px', border: '1px solid ' + (darkMode ? '#555' : '#dee2e6'), textAlign: 'left' }}>Date</th>
                  <th style={{ padding: '10px', border: '1px solid ' + (darkMode ? '#555' : '#dee2e6'), textAlign: 'center' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {unresolvedViolations.map(violation => (
                  <tr key={violation.id} style={{
                    borderBottom: '1px solid ' + (darkMode ? '#555' : '#dee2e6'),
                    backgroundColor: darkMode ? '#2d2d2d' : 'transparent'
                  }}>
                    <td style={{ padding: '10px', fontWeight: 'bold' }}>
                      {employeeDetails[violation.employeeId]?.name || violation.employeeId}
                    </td>
                    <td style={{ padding: '10px' }}>
                      <span style={{
                        padding: '4px 8px',
                        borderRadius: '4px',
                        backgroundColor: '#f44336',
                        color: 'white',
                        fontSize: '12px',
                        fontWeight: 'bold'
                      }}>
                        {violation.violationType}
                      </span>
                    </td>
                    <td style={{ padding: '10px' }}>{violation.description}</td>
                    <td style={{ padding: '10px' }}>{violation.date}</td>
                    <td style={{ padding: '10px', textAlign: 'center' }}>
                      <button
                        onClick={() => resolveViolation(violation.id)}
                        style={getButtonStyle('primary')}
                      >
                        Resolve
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>

        <div style={{ marginTop: 20, textAlign: 'right' }}>
          <button
            onClick={() => setShowCompliancePanel(false)}
            style={getButtonStyle('secondary')}
          >
            Close
          </button>
        </div>
      </div>
    );
  };

  // Render OT request panel
  const renderOTRequestPanel = () => {
    return (
      <div className="auto-height-panel full-width" style={{
        marginBottom: 20,
        padding: 20,
        backgroundColor: darkMode ? '#5c4a1e' : '#fff3cd',
        borderRadius: 8,
        border: '2px solid ' + (darkMode ? '#7a6128' : '#ffeaa7'),
        color: darkMode ? '#ffffff' : '#856404'
      }}>
        <h3 style={{ marginTop: 0 }}>📝 Request Overtime</h3>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(250px, 1fr))', gap: 15, marginBottom: 20 }}>
          <div>
            <label>Employee:</label>
            <select
              value={selectedOTEmployee}
              onChange={(e) => setSelectedOTEmployee(e.target.value)}
              style={getInputStyle()}
            >
               <option value="">Select Employee</option>
                  {/* FIX: Use employeeDetails */}
                  {Object.values(employeeDetails).map(emp => (
                    <option key={emp.id} value={emp.id}>
                      {emp.name} ({emp.department})
                    </option>
              ))}
            </select>
          </div>
           <div>
             <label>Select Employee:</label>
             <select
               value={timeClockSelectedEmployee}
               onChange={(e) => setTimeClockSelectedEmployee(e.target.value)}
               style={getInputStyle()}
             >
               <option value="">Select Employee</option>
               {Object.values(employeeDetails).map(emp => (
                 <option key={emp.id} value={emp.id}>
                   {emp.name} ({emp.department}) - {emp.position}
                 </option>
               ))}
             </select>
           </div>
          <div>
            <label>Date:</label>
            <input
              type="date"
              value={otRequestDate}
              onChange={(e) => setOTRequestDate(e.target.value)}
              style={getInputStyle()}
            />
          </div>

          <div>
            <label>Hours:</label>
            <input
              type="number"
              step="0.5"
              min="0.5"
              max={systemConfig?.maxDailyOTHours || 3}
              value={otRequestHours}
              onChange={(e) => setOTRequestHours(e.target.value)}
              placeholder={`Max: ${systemConfig?.maxDailyOTHours || 3} hours`}
              style={getInputStyle()}
            />
          </div>

          <div>
            <label>OT Type:</label>
            <select
              value={otRequestType}
              onChange={(e) => setOTRequestType(e.target.value)}
              style={getInputStyle()}
            >
              <option value="NORMAL">Normal OT ({(systemConfig?.otRateNormal || 1.5)}x)</option>
              <option value="HOLIDAY">Holiday OT ({(systemConfig?.otRateHoliday || 2.0)}x)</option>
            </select>
          </div>

          <div style={{ gridColumn: 'span 2' }}>
            <label>Reason:</label>
            <textarea
              value={otRequestReason}
              onChange={(e) => setOTRequestReason(e.target.value)}
              placeholder="Enter reason for overtime request..."
              style={{
                ...getInputStyle(),
                height: '80px',
                resize: 'vertical'
              }}
            />
          </div>
        </div>

        <div style={{
          backgroundColor: darkMode ? '#2d2d2d' : '#f8f9fa',
          padding: '15px',
          borderRadius: '6px',
          marginBottom: '20px'
        }}>
          <h4>OT Configuration Applied:</h4>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', gap: '10px' }}>
            <div>
              <strong>Normal Rate:</strong> {(systemConfig?.otRateNormal || 1.5)}x
            </div>
            <div>
              <strong>Holiday Rate:</strong> {(systemConfig?.otRateHoliday || 2.0)}x
            </div>
            <div>
              <strong>Max Daily OT:</strong> {(systemConfig?.maxDailyOTHours || 3)} hours
            </div>
            <div>
              <strong>Auto Approve:</strong> {systemConfig?.autoApproveOT ? 'Yes' : 'No'}
            </div>
          </div>
        </div>

        <div style={{ textAlign: 'right' }}>
          <button
            onClick={submitOTRequest}
            disabled={!selectedOTEmployee || !otRequestDate || !otRequestHours || !otRequestReason}
            style={getButtonStyle('primary', !selectedOTEmployee || !otRequestDate || !otRequestHours || !otRequestReason)}
          >
            📤 Submit OT Request
          </button>
          <button
            onClick={() => setShowOTRequestPanel(false)}
            style={getButtonStyle('secondary')}
          >
            Cancel
          </button>
        </div>
      </div>
    );
  };

  // Render leave coverage panel
  const renderLeaveCoveragePanel = () => {
    const pendingRequests = leaveCoverageRequests.filter(r => r.status === 'PENDING');
    const assignedRequests = leaveCoverageRequests.filter(r => r.status === 'ASSIGNED');
    const completedRequests = leaveCoverageRequests.filter(r => r.status === 'COMPLETED');

    return (
      <div className="auto-height-panel full-width" style={{
        marginBottom: 20,
        padding: 20,
        backgroundColor: darkMode ? '#1e3a5f' : '#e7f3ff',
        borderRadius: 8,
        border: '2px solid ' + (darkMode ? '#2d4d7a' : '#bee5eb'),
        color: darkMode ? '#ffffff' : '#0c5460'
       }}>
        <div style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 20
        }}>
          <h3 style={{ marginTop: 0 }}>🔄 Leave Coverage Management</h3>
          <button
            className="back-button"
            onClick={() => setShowLeaveCoveragePanel(false)}
            style={{
              padding: '8px 16px',
              background: '#6c757d',
              color: 'white',
              border: 'none',
              borderRadius: '4px',
              cursor: 'pointer',
              fontWeight: 'bold',
              display: 'flex',
              alignItems: 'center',
              gap: '5px'
            }}
          >
            ← Back
          </button>
        </div>

        {/* Manager Info */}
        <div style={{
          padding: '15px',
          backgroundColor: darkMode ? '#2d4d7a' : '#bee5eb',
          borderRadius: '6px',
          marginBottom: '20px',
          display: 'flex',
          alignItems: 'center',
          gap: '15px'
        }}>
          <div style={{
            width: '50px',
            height: '50px',
            backgroundColor: darkMode ? '#2196F3' : '#17a2b8',
            borderRadius: '50%',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: '24px',
            fontWeight: 'bold',
            color: 'white'
          }}>
            👨‍💼
          </div>
          <div>
            <h4 style={{ margin: 0 }}>Manager: {managerId}</h4>
            <p style={{ margin: '5px 0 0 0', opacity: 0.8 }}>
              You have authority to assign OT coverage for employees on leave
            </p>
          </div>
        </div>

        {/* Statistics */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(150px, 1fr))',
          gap: '10px',
          marginBottom: '20px'
        }}>
          <div style={{
            padding: '15px',
            backgroundColor: darkMode ? '#3d3d3d' : '#ffffff',
            borderRadius: '6px',
            textAlign: 'center'
          }}>
            <div style={{ fontSize: '24px', fontWeight: 'bold', color: '#FF9800' }}>
              {pendingRequests.length}
            </div>
            <div>Pending</div>
          </div>
          <div style={{
            padding: '15px',
            backgroundColor: darkMode ? '#3d3d3d' : '#ffffff',
            borderRadius: '6px',
            textAlign: 'center'
          }}>
            <div style={{ fontSize: '24px', fontWeight: 'bold', color: '#17a2b8' }}>
              {assignedRequests.length}
            </div>
            <div>Assigned</div>
          </div>
          <div style={{
            padding: '15px',
            backgroundColor: darkMode ? '#3d3d3d' : '#ffffff',
            borderRadius: '6px',
            textAlign: 'center'
          }}>
            <div style={{ fontSize: '24px', fontWeight: 'bold', color: '#4CAF50' }}>
              {completedRequests.length}
            </div>
            <div>Completed</div>
          </div>
          <div style={{
            padding: '15px',
            backgroundColor: darkMode ? '#3d3d3d' : '#ffffff',
            borderRadius: '6px',
            textAlign: 'center'
          }}>
            <div style={{ fontSize: '24px', fontWeight: 'bold', color: '#2196F3' }}>
              {leaveCoverageRequests.length}
            </div>
            <div>Total</div>
          </div>
        </div>

        {/* Pending Requests */}
        <div style={{ marginBottom: 30 }}>
          <h4>⏳ Pending Coverage Requests ({pendingRequests.length})</h4>
          {loadingCoverageRequests ? (
            <div style={{ textAlign: 'center', padding: '40px' }}>
              <div className="loading-spinner"></div>
              <div>Loading coverage requests...</div>
            </div>
          ) : pendingRequests.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '40px', backgroundColor: darkMode ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.03)', borderRadius: '6px', color: darkMode ? '#cccccc' : '#666' }}>
              <div style={{ fontSize: '48px', marginBottom: '20px' }}>✅</div>
              <div style={{ fontSize: '16px', fontWeight: 'bold' }}>No pending coverage requests</div>
              <div style={{ fontSize: '14px', opacity: 0.8, marginTop: '10px' }}>
                All leave coverage has been assigned
              </div>
            </div>
          ) : (
            <div className="no-individual-scroll full-width">
              <table style={{
                width: '100%',
                borderCollapse: 'collapse',
                backgroundColor: darkMode ? '#2d2d2d' : '#ffffff',
                color: darkMode ? '#ffffff' : 'inherit'
              }}>
                <thead>
                  <tr style={{ backgroundColor: darkMode ? '#3d3d3d' : '#f8f9fa' }}>
                    <th style={{ padding: '10px', border: '1px solid ' + (darkMode ? '#555' : '#dee2e6'), textAlign: 'left' }}>Absent Employee</th>
                    <th style={{ padding: '10px', border: '1px solid ' + (darkMode ? '#555' : '#dee2e6'), textAlign: 'left' }}>Date</th>
                    <th style={{ padding: '10px', border: '1px solid ' + (darkMode ? '#555' : '#dee2e6'), textAlign: 'left' }}>Shift</th>
                    <th style={{ padding: '10px', border: '1px solid ' + (darkMode ? '#555' : '#dee2e6'), textAlign: 'left' }}>Required Skills</th>
                    <th style={{ padding: '10px', border: '1px solid ' + (darkMode ? '#555' : '#dee2e6'), textAlign: 'center', width: '300px' }}>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {pendingRequests.map(request => {
                    const absentEmp = employeeDetails[request.absentEmployeeId];
                    const suitableCount = request.suitableEmployees ? request.suitableEmployees.length : 0;

                    return (
                      <tr key={request.id} style={{
                        borderBottom: '1px solid ' + (darkMode ? '#555' : '#dee2e6'),
                        backgroundColor: darkMode ? '#2d2d2d' : 'transparent'
                      }}>
                        <td style={{ padding: '10px', fontWeight: 'bold' }}>
                          {absentEmp?.name || request.absentEmployeeId}
                          <div style={{ fontSize: '12px', color: darkMode ? '#cccccc' : '#666' }}>
                            {absentEmp?.department || 'Unknown Department'}
                          </div>
                        </td>
                        <td style={{ padding: '10px', fontWeight: 'bold' }}>{request.leaveDate}</td>
                        <td style={{ padding: '10px' }}>
                          <div style={{
                            padding: '4px 8px',
                            backgroundColor: SHIFT_CONFIG[request.shiftName]?.color + '20',
                            borderRadius: '4px',
                            display: 'inline-block',
                            borderLeft: '4px solid ' + (SHIFT_CONFIG[request.shiftName]?.color || '#607D8B')
                          }}>
                            {request.shiftName}
                          </div>
                        </td>
                        <td style={{ padding: '10px' }}>
                          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '5px' }}>
                            {request.requiredSkills && Array.isArray(request.requiredSkills) ?
                              request.requiredSkills.slice(0, 3).map((skill, idx) => (
                                <span key={idx} style={{
                                  padding: '2px 6px',
                                  backgroundColor: darkMode ? '#3d3d3d' : '#f1f1f1',
                                  borderRadius: '12px',
                                  fontSize: '11px'
                                }}>
                                  {skill}
                                </span>
                              )) : 'No skills specified'}
                            {request.requiredSkills && request.requiredSkills.length > 3 && (
                              <span style={{
                                padding: '2px 6px',
                                backgroundColor: darkMode ? '#4CAF50' : '#4CAF50',
                                color: 'white',
                                borderRadius: '12px',
                                fontSize: '11px',
                                fontWeight: 'bold'
                              }}>
                                +{request.requiredSkills.length - 3} more
                              </span>
                            )}
                          </div>
                          <div style={{
                            padding: '15px',
                            backgroundColor: darkMode ? '#2d2d2d' : '#f8f9fa',
                            borderRadius: '6px',
                            marginBottom: '15px'
                          }}>
                            <div style={{
                              display: 'flex',
                              justifyContent: 'space-between',
                              alignItems: 'center',
                              marginBottom: '10px'
                            }}>
                              <div>
                                <label style={{ fontWeight: 'bold' }}>Skill Match Threshold:</label>
                                <div style={{ fontSize: '12px', color: darkMode ? '#cccccc' : '#666' }}>
                                  Minimum skill similarity required (Current: {skillThreshold}%)
                                </div>
                              </div>
                              <div style={{
                                padding: '5px 10px',
                                backgroundColor: skillThreshold <= 30 ? '#4CAF50' :
                                                skillThreshold <= 50 ? '#FF9800' : '#F44336',
                                color: 'white',
                                borderRadius: '4px',
                                fontWeight: 'bold',
                                fontSize: '14px'
                              }}>
                                {skillThreshold}%
                              </div>
                            </div>

                            <input
                              type="range"
                              min="10"
                              max="100"
                              step="5"
                              value={skillThreshold}
                              onChange={(e) => {
                                const newThreshold = parseInt(e.target.value);
                                setSkillThreshold(newThreshold);
                                if (selectedCoverageRequest) {
                                  loadSuitableEmployees(selectedCoverageRequest.id, newThreshold);
                                }
                              }}
                              style={{
                                width: '100%',
                                height: '8px',
                                borderRadius: '4px',
                                background: darkMode ? '#444' : '#e9ecef',
                                outline: 'none'
                              }}
                            />

                            <div style={{
                              display: 'flex',
                              justifyContent: 'space-between',
                              fontSize: '11px',
                              marginTop: '5px',
                              color: darkMode ? '#cccccc' : '#666'
                            }}>
                              <span>Low (10%)</span>
                              <span>Medium (50%)</span>
                              <span>High (100%)</span>
                            </div>

                            <div style={{
                              fontSize: '12px',
                              marginTop: '10px',
                              padding: '8px',
                              backgroundColor: darkMode ? '#3d3d3d' : '#ffffff',
                              borderRadius: '4px',
                              border: '1px solid ' + (darkMode ? '#555' : '#e9ecef')
                            }}>
                              <strong>Skill Match Guidelines:</strong>
                              <ul style={{ margin: '5px 0 0 15px', padding: 0 }}>
                                <li><span style={{ color: '#4CAF50' }}>10-30%:</span> Any skill overlap</li>
                                <li><span style={{ color: '#FF9800' }}>30-70%:</span> Moderate skill match</li>
                                <li><span style={{ color: '#F44336' }}>70-100%:</span> Near identical skills</li>
                              </ul>
                            </div>
                          </div>
                          {/* Show suitable employees count */}
                          <div style={{ fontSize: '11px', marginTop: '5px', color: '#4CAF50', fontWeight: 'bold' }}>
                            {suitableEmployees.length} suitable employees available
                          </div>
                        </td>
                        <td style={{ padding: '10px', textAlign: 'center' }}>
                          <div style={{ display: 'flex', gap: '10px', justifyContent: 'center' }}>
                            <button
                              onClick={() => {
                                setSelectedCoverageRequest(request);
                                loadSuitableEmployees(request.id);
                              }}
                              style={{
                                padding: '8px 16px',
                                backgroundColor: '#4CAF50',
                                color: 'white',
                                border: 'none',
                                borderRadius: '4px',
                                cursor: 'pointer',
                                fontWeight: 'bold'
                              }}
                              title="Assign coverage"
                            >
                              👥 Assign Suitable
                            </button>
                            <button
                              onClick={() => cancelCoverage(request.id)}
                              style={{
                                padding: '8px 16px',
                                backgroundColor: '#F44336',
                                color: 'white',
                                border: 'none',
                                borderRadius: '4px',
                                cursor: 'pointer',
                                fontWeight: 'bold'
                              }}
                              title="Cancel this request"
                            >
                              ❌ Cancel
                            </button>
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* Assignment Form */}
        {selectedCoverageRequest && (
          <div style={{
            padding: '20px',
            backgroundColor: darkMode ? '#2d2d2d' : '#f8f9fa',
            borderRadius: '8px',
            marginBottom: '20px',
            border: '2px solid ' + (darkMode ? '#444' : '#e9ecef')
          }}>
            <div style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              marginBottom: '15px'
            }}>
              <h4 style={{ margin: 0 }}>
                🔄 Assign OT Coverage for {employeeDetails[selectedCoverageRequest.absentEmployeeId]?.name || selectedCoverageRequest.absentEmployeeId}
              </h4>
              <button
                onClick={() => {
                  setSelectedCoverageRequest(null);
                  setSelectedCoverageEmployee('');
                  setSelectedOTHours('1');
                  setCoverageType('COVERAGE');
                }}
                style={{
                  padding: '6px 12px',
                  backgroundColor: '#6c757d',
                  color: 'white',
                  border: 'none',
                  borderRadius: '4px',
                  cursor: 'pointer'
                }}
              >
                ✕ Close
              </button>
            </div>

            {/* Absent Employee Info */}
            <div style={{
              padding: '15px',
              backgroundColor: darkMode ? '#3d3d3d' : '#e9ecef',
              borderRadius: '6px',
              marginBottom: '15px'
            }}>
              <div style={{ fontWeight: 'bold', marginBottom: '8px', color: '#F44336' }}>
                ⛔ Absent Employee Information
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
                <div>
                  <div style={{ fontSize: '12px', color: darkMode ? '#cccccc' : '#666' }}>Employee</div>
                  <div style={{ fontWeight: 'bold' }}>
                    {employeeDetails[selectedCoverageRequest.absentEmployeeId]?.name || selectedCoverageRequest.absentEmployeeId}
                  </div>
                </div>
                <div>
                  <div style={{ fontSize: '12px', color: darkMode ? '#cccccc' : '#666' }}>Date & Shift</div>
                  <div style={{ fontWeight: 'bold' }}>
                    {selectedCoverageRequest.leaveDate} - {selectedCoverageRequest.shiftName} Shift
                  </div>
                </div>
              </div>
              {absentEmployeeSkills.length > 0 && (
                <div style={{ marginTop: '10px' }}>
                  <div style={{ fontSize: '12px', color: darkMode ? '#cccccc' : '#666' }}>Required Skills</div>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: '5px', marginTop: '5px' }}>
                    {absentEmployeeSkills.map((skill, idx) => (
                      <span key={idx} style={{
                        padding: '4px 8px',
                        backgroundColor: darkMode ? '#4CAF50' : '#4CAF50',
                        color: 'white',
                        borderRadius: '12px',
                        fontSize: '11px',
                        fontWeight: 'bold'
                      }}>
                        {skill}
                      </span>
                    ))}
                  </div>
                </div>
              )}
            </div>

            {/* Coverage Configuration */}
            <div style={{ marginBottom: '15px' }}>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '15px' }}>
                <div>
                  <label>OT Hours:</label>
                  <div style={{ display: 'flex', gap: '10px', marginTop: '5px' }}>
                    {[1, 2, 3].map(hours => (
                      <button
                        key={hours}
                        onClick={() => setSelectedOTHours(hours.toString())}
                        style={{
                          flex: 1,
                          padding: '10px',
                          backgroundColor: selectedOTHours === hours.toString() ?
                            (darkMode ? '#4CAF50' : '#4CAF50') :
                            (darkMode ? '#444' : '#e9ecef'),
                          color: selectedOTHours === hours.toString() ? 'white' :
                            (darkMode ? '#ffffff' : '#333'),
                          border: '2px solid ' + (selectedOTHours === hours.toString() ? '#2E7D32' : '#ccc'),
                          borderRadius: '6px',
                          cursor: 'pointer',
                          fontWeight: 'bold',
                          fontSize: '14px',
                          transition: 'all 0.3s ease'
                        }}
                      >
                        {hours}h
                      </button>
                    ))}
                  </div>
                </div>

                <div>
                  <label>Coverage Type:</label>
                  <select
                    value={coverageType}
                    onChange={(e) => setCoverageType(e.target.value)}
                    style={getInputStyle()}
                  >
                    <option value="COVERAGE">Regular Coverage (1.2x OT)</option>
                    <option value="EMERGENCY">Emergency Coverage (1.5x OT)</option>
                    <option value="HOLIDAY">Holiday Coverage (2.0x OT)</option>
                  </select>
                </div>

                <div>
                  <label>Select Employee:</label>
                  <select
                    value={selectedCoverageEmployee}
                    onChange={(e) => setSelectedCoverageEmployee(e.target.value)}
                    style={getInputStyle()}
                  >
                    <option value="">Select suitable employee</option>
                    {suitableEmployees.map(emp => {
                      const otWage = employeeOTWages[emp.id]?.[`${selectedOTHours}h`] || 0;
                      return (
                        <option key={emp.id} value={emp.id}>
                          {emp.name} ({emp.department}) - {emp.skillScore.toFixed(0)}% match - ${otWage.toFixed(2)}
                        </option>
                      );
                    })}
                  </select>
                </div>
              </div>
            </div>

            {/* Selected Employee Details */}
            {selectedCoverageEmployee && (
              <div style={{
                padding: '15px',
                backgroundColor: darkMode ? '#1e3a5f' : '#e3f2fd',
                borderRadius: '6px',
                marginBottom: '15px',
                border: '1px solid ' + (darkMode ? '#2d4d7a' : '#90caf9')
              }}>
                <div style={{ fontWeight: 'bold', marginBottom: '10px', color: darkMode ? '#ffffff' : '#1565c0' }}>
                  👤 Selected Employee Details
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '10px' }}>
                  <div>
                    <div style={{ fontSize: '12px', color: darkMode ? '#cccccc' : '#666' }}>Name</div>
                    <div style={{ fontWeight: 'bold' }}>
                      {suitableEmployees.find(e => e.id === selectedCoverageEmployee)?.name}
                    </div>
                  </div>
                  <div>
                    <div style={{ fontSize: '12px', color: darkMode ? '#cccccc' : '#666' }}>Skill Match</div>
                    <div style={{ fontWeight: 'bold', color: '#4CAF50' }}>
                      {suitableEmployees.find(e => e.id === selectedCoverageEmployee)?.skillScore.toFixed(0)}%
                    </div>
                  </div>
                  <div>
                    <div style={{ fontSize: '12px', color: darkMode ? '#cccccc' : '#666' }}>Estimated OT Pay</div>
                    <div style={{ fontWeight: 'bold', color: '#FF9800', fontSize: '16px' }}>
                      ${(employeeOTWages[selectedCoverageEmployee]?.[`${selectedOTHours}h`] || 0).toFixed(2)}
                    </div>
                  </div>
                </div>

                {/* Skills Display */}
                {suitableEmployees.find(e => e.id === selectedCoverageEmployee)?.skills && (
                  <div style={{ marginTop: '10px' }}>
                    <div style={{ fontSize: '12px', color: darkMode ? '#cccccc' : '#666' }}>Skills:</div>
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: '5px', marginTop: '5px' }}>
                      {suitableEmployees.find(e => e.id === selectedCoverageEmployee).skills.map((skill, idx) => (
                        <span key={idx} style={{
                          padding: '3px 8px',
                          backgroundColor: absentEmployeeSkills.includes(skill) ?
                            '#4CAF50' : (darkMode ? '#444' : '#e9ecef'),
                          color: absentEmployeeSkills.includes(skill) ? 'white' :
                            (darkMode ? '#ffffff' : '#333'),
                          borderRadius: '12px',
                          fontSize: '11px',
                          border: absentEmployeeSkills.includes(skill) ? '1px solid #2E7D32' : '1px solid #ccc'
                        }}>
                          {skill} {absentEmployeeSkills.includes(skill) && '✓'}
                        </span>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            )}

            {/* OT Rate Information */}
            <div style={{
              padding: '10px',
              backgroundColor: darkMode ? '#5c4a1e' : '#fff3cd',
              borderRadius: '6px',
              marginBottom: '15px',
              border: '1px solid ' + (darkMode ? '#7a6128' : '#ffeaa7')
            }}>
              <div style={{ fontSize: '12px', color: darkMode ? '#ffcc80' : '#856404' }}>
                ℹ️ OT Rates: Regular (1.2x) • Emergency (1.5x) • Holiday (2.0x) | Max OT per day: {systemConfig?.maxDailyOTHours || 3}h
              </div>
            </div>

            <div style={{ textAlign: 'right' }}>
              <button
                onClick={() => assignCoverage(
                  selectedCoverageRequest.id,
                  selectedCoverageEmployee,
                  selectedOTHours,
                  coverageType
                )}
                disabled={!selectedCoverageEmployee}
                style={getButtonStyle('primary', !selectedCoverageEmployee)}
              >
                ✅ Assign OT Coverage ({selectedOTHours}h)
              </button>
            </div>
          </div>
        )}

        {/* Assigned Coverages */}
        <div style={{ marginBottom: 30 }}>
          <h4>✅ Assigned Coverages ({assignedRequests.length})</h4>
          {assignedRequests.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '20px', color: darkMode ? '#cccccc' : '#666' }}>
              No assigned coverages
            </div>
          ) : (
            <div className="no-individual-scroll full-width">
              <table style={{
                width: '100%',
                borderCollapse: 'collapse',
                backgroundColor: darkMode ? '#2d2d2d' : '#ffffff',
                color: darkMode ? '#ffffff' : 'inherit'
              }}>
                <thead>
                  <tr style={{ backgroundColor: darkMode ? '#3d3d3d' : '#f8f9fa' }}>
                    <th style={{ padding: '10px', border: '1px solid ' + (darkMode ? '#555' : '#dee2e6'), textAlign: 'left' }}>Covering Employee</th>
                    <th style={{ padding: '10px', border: '1px solid ' + (darkMode ? '#555' : '#dee2e6'), textAlign: 'left' }}>For Absent Employee</th>
                    <th style={{ padding: '10px', border: '1px solid ' + (darkMode ? '#555' : '#dee2e6'), textAlign: 'left' }}>Date</th>
                    <th style={{ padding: '10px', border: '1px solid ' + (darkMode ? '#555' : '#dee2e6'), textAlign: 'left' }}>Hours</th>
                    <th style={{ padding: '10px', border: '1px solid ' + (darkMode ? '#555' : '#dee2e6'), textAlign: 'center' }}>Status</th>
                    <th style={{ padding: '10px', border: '1px solid ' + (darkMode ? '#555' : '#dee2e6'), textAlign: 'center' }}>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {assignedRequests.map(request => {
                    const assignments = request.assignments || [];
                    return assignments.map(assignment => {
                      const coveringEmp = employeeDetails[assignment.assignedEmployeeId];
                      const absentEmp = employeeDetails[assignment.coveredEmployeeId];

                      return (
                        <tr key={assignment.id} style={{
                          borderBottom: '1px solid ' + (darkMode ? '#555' : '#dee2e6'),
                          backgroundColor: darkMode ? '#2d2d2d' : 'transparent'
                        }}>
                          <td style={{ padding: '10px', fontWeight: 'bold' }}>
                            {coveringEmp?.name || assignment.assignedEmployeeId}
                            <div style={{ fontSize: '12px', color: darkMode ? '#cccccc' : '#666' }}>
                              {coveringEmp?.department}
                            </div>
                          </td>
                          <td style={{ padding: '10px' }}>
                            {absentEmp?.name || assignment.coveredEmployeeId}
                            <div style={{ fontSize: '12px', color: darkMode ? '#cccccc' : '#666' }}>
                              {absentEmp?.department}
                            </div>
                          </td>
                          <td style={{ padding: '10px' }}>{assignment.coverageDate}</td>
                          <td style={{ padding: '10px', fontWeight: 'bold' }}>
                            {assignment.assignedHours} hours
                            <div style={{ fontSize: '12px', color: '#FF9800' }}>
                              OT Rate: {(systemConfig?.otRateNormal || 1.5)}x
                            </div>
                          </td>
                          <td style={{ padding: '10px', textAlign: 'center' }}>
                            {getCoverageStatusBadge(assignment.status)}
                          </td>
                          <td style={{ padding: '10px', textAlign: 'center' }}>
                            <button
                              onClick={() => completeCoverage(request.id)}
                              style={{
                                padding: '6px 12px',
                                backgroundColor: '#4CAF50',
                                color: 'white',
                                border: 'none',
                                borderRadius: '4px',
                                cursor: 'pointer',
                                fontWeight: 'bold',
                                fontSize: '12px'
                              }}
                            >
                              ✅ Complete
                            </button>
                          </td>
                        </tr>
                      );
                    });
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    );
  };

  // Render notifications panel
  const renderNotificationsPanel = () => {
    const unreadNotifications = notifications.filter(n => !n.read);
    const readNotifications = notifications.filter(n => n.read);

    return (
      <div className="auto-height-panel full-width" style={{
        marginBottom: 20,
        padding: 20,
        backgroundColor: darkMode ? '#5c4a1e' : '#fff3cd',
        borderRadius: 8,
        border: '2px solid ' + (darkMode ? '#7a6128' : '#ffeaa7'),
        color: darkMode ? '#ffffff' : '#856404',
        maxHeight: '600px',
        overflow: 'hidden'
      }}>
        <div style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 20
        }}>
          <h3 style={{ marginTop: 0, display: 'flex', alignItems: 'center', gap: '10px' }}>
            🔔 Notifications
            {unreadNotificationCount > 0 && (
              <span style={{
                backgroundColor: '#f44336',
                color: 'white',
                borderRadius: '50%',
                width: '24px',
                height: '24px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: '12px',
                fontWeight: 'bold'
              }}>
                {unreadNotificationCount}
              </span>
            )}
          </h3>
          <div style={{ display: 'flex', gap: '10px' }}>
            {unreadNotificationCount > 0 && (
              <button
                onClick={markAllNotificationsAsRead}
                style={{
                  padding: '8px 16px',
                  backgroundColor: '#4CAF50',
                  color: 'white',
                  border: 'none',
                  borderRadius: '4px',
                  cursor: 'pointer',
                  fontWeight: 'bold',
                  fontSize: '12px'
                }}
              >
                📭 Mark All Read
              </button>
            )}
            <button
              onClick={() => setShowNotificationsPanel(false)}
              style={{
                padding: '8px 16px',
                background: '#6c757d',
                color: 'white',
                border: 'none',
                borderRadius: '4px',
                cursor: 'pointer',
                fontWeight: 'bold',
                display: 'flex',
                alignItems: 'center',
                gap: '5px'
              }}
            >
              ✕ Close
            </button>
          </div>
        </div>

        <div style={{ overflowY: 'auto', maxHeight: '500px' }}>
          {/* Unread Notifications */}
          {unreadNotifications.length > 0 && (
            <div style={{ marginBottom: '20px' }}>
              <h4>Unread ({unreadNotifications.length})</h4>
              {unreadNotifications.map(notification => (
                <div key={notification.id} style={{
                  padding: '15px',
                  marginBottom: '10px',
                  backgroundColor: darkMode ? '#2d2d2d' : '#ffffff',
                  borderRadius: '8px',
                  border: '2px solid ' + (darkMode ? '#FF9800' : '#FFC107'),
                  boxShadow: '0 2px 4px rgba(0,0,0,0.1)'
                }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                    <div style={{ flex: 1 }}>
                      <div style={{ fontWeight: 'bold', marginBottom: '5px' }}>
                        {getNotificationTitle(notification.type)}
                      </div>
                      <div style={{ fontSize: '14px', marginBottom: '10px' }}>
                        {notification.message}
                      </div>
                      <div style={{ fontSize: '12px', color: darkMode ? '#cccccc' : '#666' }}>
                        {new Date(notification.timestamp).toLocaleString()}
                      </div>
                    </div>
                    <button
                      onClick={() => markNotificationAsRead(notification.id)}
                      style={{
                        padding: '4px 8px',
                        backgroundColor: '#4CAF50',
                        color: 'white',
                        border: 'none',
                        borderRadius: '4px',
                        cursor: 'pointer',
                        fontSize: '11px',
                        marginLeft: '10px'
                      }}
                    >
                      ✓ Read
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* Read Notifications */}
          {readNotifications.length > 0 && (
            <div>
              <h4>Read ({readNotifications.length})</h4>
              {readNotifications.map(notification => (
                <div key={notification.id} style={{
                  padding: '15px',
                  marginBottom: '10px',
                  backgroundColor: darkMode ? '#2d2d2d' : '#ffffff',
                  borderRadius: '8px',
                  border: '1px solid ' + (darkMode ? '#555' : '#e9ecef'),
                  opacity: 0.7
                }}>
                  <div style={{ fontWeight: 'bold', marginBottom: '5px' }}>
                    {getNotificationTitle(notification.type)} ✓
                  </div>
                  <div style={{ fontSize: '14px', marginBottom: '5px' }}>
                    {notification.message}
                  </div>
                  <div style={{ fontSize: '12px', color: darkMode ? '#cccccc' : '#666' }}>
                    {new Date(notification.timestamp).toLocaleString()}
                  </div>
                </div>
              ))}
            </div>
          )}

          {notifications.length === 0 && (
            <div style={{ textAlign: 'center', padding: '40px', color: darkMode ? '#cccccc' : '#666' }}>
              <div style={{ fontSize: '48px', marginBottom: '20px' }}>📭</div>
              <div style={{ fontSize: '16px', fontWeight: 'bold' }}>No notifications</div>
              <div style={{ fontSize: '14px', opacity: 0.8, marginTop: '10px' }}>
                You're all caught up!
              </div>
            </div>
          )}
        </div>
      </div>
    );
  };

  // Render OT management panel
  const renderOTManagementPanel = () => {
    const pendingRequests = otRequests.filter(r => r.status === 'PENDING');

    return (
      <div className="auto-height-panel full-width" style={{
        marginBottom: 20,
        padding: 20,
        backgroundColor: darkMode ? '#1a472a' : '#d4edda',
        borderRadius: 8,
        border: '2px solid ' + (darkMode ? '#2d5a3a' : '#c3e6cb'),
        color: darkMode ? '#ffffff' : '#155724'
      }}>
        <div style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 20
        }}>
          <h3 style={{ marginTop: 0 }}>💰 Overtime Management</h3>
          <button
            className="back-button"
            onClick={() => setShowOTManagementPanel(false)}
            style={{
              padding: '8px 16px',
              background: '#6c757d',
              color: 'white',
              border: 'none',
              borderRadius: '4px',
              cursor: 'pointer',
              fontWeight: 'bold',
              display: 'flex',
              alignItems: 'center',
              gap: '5px'
            }}
          >
            ← Back
          </button>
        </div>

        {/* Manager Info */}
        <div style={{
          padding: '15px',
          backgroundColor: darkMode ? '#2d5a3a' : '#c3e6cb',
          borderRadius: '6px',
          marginBottom: '20px',
          display: 'flex',
          alignItems: 'center',
          gap: '15px'
        }}>
          <div style={{
            width: '50px',
            height: '50px',
            backgroundColor: darkMode ? '#4CAF50' : '#28a745',
            borderRadius: '50%',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: '24px',
            fontWeight: 'bold',
            color: 'white'
          }}>
            👨‍💼
          </div>
          <div>
            <h4 style={{ margin: 0 }}>Manager: {managerId}</h4>
            <p style={{ margin: '5px 0 0 0', opacity: 0.8 }}>
              You have authority to approve/reject all overtime requests
            </p>
          </div>
        </div>

        {/* Pending Requests */}
        <div style={{ marginBottom: 30 }}>
          <h4>⏳ Pending Approval ({pendingRequests.length})</h4>
          {pendingRequests.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '40px', backgroundColor: darkMode ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.03)', borderRadius: '6px', color: darkMode ? '#cccccc' : '#666' }}>
              <div style={{ fontSize: '48px', marginBottom: '20px' }}>✅</div>
              <div style={{ fontSize: '16px', fontWeight: 'bold' }}>No pending overtime requests</div>
              <div style={{ fontSize: '14px', opacity: 0.8, marginTop: '10px' }}>
                All OT requests have been processed
              </div>
            </div>
          ) : (
            <div className="no-individual-scroll full-width">
              <table style={{
                width: '100%',
                borderCollapse: 'collapse',
                backgroundColor: darkMode ? '#2d2d2d' : '#ffffff',
                color: darkMode ? '#ffffff' : 'inherit'
              }}>
                <thead>
                  <tr style={{ backgroundColor: darkMode ? '#3d3d3d' : '#f8f9fa' }}>
                    <th style={{ padding: '10px', border: '1px solid ' + (darkMode ? '#555' : '#dee2e6'), textAlign: 'left' }}>Employee</th>
                    <th style={{ padding: '10px', border: '1px solid ' + (darkMode ? '#555' : '#dee2e6'), textAlign: 'left' }}>Date</th>
                    <th style={{ padding: '10px', border: '1px solid ' + (darkMode ? '#555' : '#dee2e6'), textAlign: 'left' }}>Hours</th>
                    <th style={{ padding: '10px', border: '1px solid ' + (darkMode ? '#555' : '#dee2e6'), textAlign: 'left' }}>Reason</th>
                    <th style={{ padding: '10px', border: '1px solid ' + (darkMode ? '#555' : '#dee2e6'), textAlign: 'center', width: '250px' }}>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {pendingRequests.map(request => {
                    const employee = employeeDetails[request.employeeId];

                    return (
                      <tr key={request.id} style={{
                        borderBottom: '1px solid ' + (darkMode ? '#555' : '#dee2e6'),
                        backgroundColor: darkMode ? '#2d2d2d' : 'transparent'
                      }}>
                        <td style={{ padding: '10px', fontWeight: 'bold' }}>
                          {employee?.name || request.employeeId}
                          <div style={{ fontSize: '12px', color: darkMode ? '#cccccc' : '#666' }}>
                            {employee?.department || 'Unknown Department'} • {employee?.position || 'Employee'}
                          </div>
                        </td>
                        <td style={{ padding: '10px' }}>{request.date}</td>
                        <td style={{ padding: '10px', fontWeight: 'bold' }}>{request.requestedHours} hours</td>
                        <td style={{ padding: '10px' }}>{request.reason}</td>
                        <td style={{ padding: '10px', textAlign: 'center' }}>
                          <div style={{ display: 'flex', gap: '10px', justifyContent: 'center' }}>
                            <button
                              onClick={() => approveOTRequest(request.id)}
                              style={{
                                padding: '8px 16px',
                                backgroundColor: '#4CAF50',
                                color: 'white',
                                border: 'none',
                                borderRadius: '4px',
                                cursor: 'pointer',
                                fontWeight: 'bold'
                              }}
                              title="Approve this request"
                            >
                              ✅ Approve
                            </button>
                            <button
                              onClick={() => rejectOTRequest(request.id)}
                              style={{
                                padding: '8px 16px',
                                backgroundColor: '#F44336',
                                color: 'white',
                                border: 'none',
                                borderRadius: '4px',
                                cursor: 'pointer',
                                fontWeight: 'bold'
                              }}
                              title="Reject this request"
                            >
                              ❌ Reject
                            </button>
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* Statistics */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(150px, 1fr))',
          gap: '10px',
          marginBottom: '20px'
        }}>
          <div style={{
            padding: '15px',
            backgroundColor: darkMode ? '#3d3d3d' : '#ffffff',
            borderRadius: '6px',
            textAlign: 'center'
          }}>
            <div style={{ fontSize: '24px', fontWeight: 'bold', color: '#FF9800' }}>
              {pendingRequests.length}
            </div>
            <div>Pending</div>
          </div>
          <div style={{
            padding: '15px',
            backgroundColor: darkMode ? '#3d3d3d' : '#ffffff',
            borderRadius: '6px',
            textAlign: 'center'
          }}>
            <div style={{ fontSize: '24px', fontWeight: 'bold', color: '#4CAF50' }}>
              {otRequests.filter(r => r.status === 'APPROVED').length}
            </div>
            <div>Approved</div>
          </div>
          <div style={{
            padding: '15px',
            backgroundColor: darkMode ? '#3d3d3d' : '#ffffff',
            borderRadius: '6px',
            textAlign: 'center'
          }}>
            <div style={{ fontSize: '24px', fontWeight: 'bold', color: '#F44336' }}>
              {otRequests.filter(r => r.status === 'REJECTED').length}
            </div>
            <div>Rejected</div>
          </div>
          <div style={{
            padding: '15px',
            backgroundColor: darkMode ? '#3d3d3d' : '#ffffff',
            borderRadius: '6px',
            textAlign: 'center'
          }}>
            <div style={{ fontSize: '24px', fontWeight: 'bold', color: '#2196F3' }}>
              {otRequests.length}
            </div>
            <div>Total</div>
          </div>
        </div>
      </div>
    );
  };

  // Render overtime panel
  const renderOvertimePanel = () => {
    const pendingRequests = otRequests.filter(r => r.status === 'PENDING');
    const approvedRequests = otRequests.filter(r => r.status === 'APPROVED');
    const rejectedRequests = otRequests.filter(r => r.status === 'REJECTED');

    return (
      <div className="auto-height-panel full-width" style={{
        marginBottom: 20,
        padding: 20,
        backgroundColor: darkMode ? '#5c4a1e' : '#fff3cd',
        borderRadius: 8,
        border: '2px solid ' + (darkMode ? '#7a6128' : '#ffeaa7'),
        color: darkMode ? '#ffffff' : '#856404'
      }}>
        <h3 style={{ marginTop: 0 }}>💰 Overtime Management</h3>

        {/* OT Configuration Summary */}
        <div style={{
          padding: '15px',
          backgroundColor: darkMode ? '#2d2d2d' : '#f8f9fa',
          borderRadius: '6px',
          marginBottom: '20px'
        }}>
          <h4>📊 OT Configuration Summary</h4>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', gap: '15px' }}>
            <div>
              <strong>Normal OT Rate:</strong> {(systemConfig?.otRateNormal || 1.5)}x
            </div>
            <div>
              <strong>Holiday OT Rate:</strong> {(systemConfig?.otRateHoliday || 2.0)}x
            </div>
            <div>
              <strong>Coverage OT Rate:</strong> {(systemConfig?.otRateCoverage || 1.2)}x
            </div>
            <div>
              <strong>Max Daily OT:</strong> {(systemConfig?.maxDailyOTHours || 3)} hours
            </div>
            <div>
              <strong>Max Weekly OT:</strong> {(systemConfig?.maxWeeklyOTHours || 15)} hours
            </div>
            <div>
              <strong>Auto Approve:</strong> {systemConfig?.autoApproveOT ? '✅ Yes' : '❌ No'}
            </div>
            <div>
              <strong>Paid OT:</strong> {systemConfig?.paidOT !== false ? '✅ Yes' : '❌ No'}
            </div>
          </div>
        </div>

        {/* Quick Actions */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))',
          gap: '15px',
          marginBottom: '20px'
        }}>
          <button
            onClick={() => setShowOTRequestPanel(true)}
            style={{
              ...getButtonStyle('primary'),
              padding: '20px',
              fontSize: '18px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: '10px'
            }}
          >
            📝 Request Overtime
          </button>

          <button
            onClick={() => setShowOTManagementPanel(true)}
            style={{
              ...getButtonStyle('info'),
              padding: '20px',
              fontSize: '18px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: '10px'
            }}
          >
            👨‍💼 Manage OT Requests
          </button>
          <button
            onClick={() => setShowDirectCoveragePanel(!showDirectCoveragePanel)}
            style={getButtonStyle('warning')}
          >
            🔄 Direct Coverage
          </button>

          <button
            onClick={openLeaveCoveragePanel}
            style={{
              ...getButtonStyle('warning'),
              padding: '20px',
              fontSize: '18px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: '10px'
            }}
          >
            🔄 Leave Coverage
          </button>
        </div>

        {/* Statistics */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(150px, 1fr))',
          gap: '10px',
          marginBottom: '20px'
        }}>
          <div style={{
            padding: '15px',
            backgroundColor: darkMode ? '#3d3d3d' : '#ffffff',
            borderRadius: '6px',
            textAlign: 'center'
          }}>
            <div style={{ fontSize: '24px', fontWeight: 'bold', color: '#FF9800' }}>
              {pendingRequests.length}
            </div>
            <div>Pending OT</div>
          </div>
          <div style={{
            padding: '15px',
            backgroundColor: darkMode ? '#3d3d3d' : '#ffffff',
            borderRadius: '6px',
            textAlign: 'center'
          }}>
            <div style={{ fontSize: '24px', fontWeight: 'bold', color: '#17a2b8' }}>
              {leaveCoverageRequests.filter(r => r.status === 'PENDING').length}
            </div>
            <div>Pending Coverage</div>
          </div>
          <div style={{
            padding: '15px',
            backgroundColor: darkMode ? '#3d3d3d' : '#ffffff',
            borderRadius: '6px',
            textAlign: 'center'
          }}>
            <div style={{ fontSize: '24px', fontWeight: 'bold', color: '#4CAF50' }}>
              {approvedRequests.length}
            </div>
            <div>Approved</div>
          </div>
          <div style={{
            padding: '15px',
            backgroundColor: darkMode ? '#3d3d3d' : '#ffffff',
            borderRadius: '6px',
            textAlign: 'center'
          }}>
            <div style={{ fontSize: '24px', fontWeight: 'bold', color: '#F44336' }}>
              {rejectedRequests.length}
            </div>
            <div>Rejected</div>
          </div>
        </div>

        {/* Recent Pending Requests */}
        <div>
          <h4>Recent Pending Requests ({pendingRequests.length})</h4>
          {pendingRequests.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '40px', backgroundColor: darkMode ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.03)', borderRadius: '6px', color: darkMode ? '#cccccc' : '#666' }}>
              <div style={{ fontSize: '48px', marginBottom: '20px' }}>✅</div>
              <div style={{ fontSize: '16px', fontWeight: 'bold' }}>No pending overtime requests</div>
              <div style={{ fontSize: '14px', opacity: 0.8, marginTop: '10px' }}>
                All OT requests are processed
              </div>
            </div>
          ) : (
            <div className="no-individual-scroll full-width">
              <table style={{
                width: '100%',
                borderCollapse: 'collapse',
                backgroundColor: darkMode ? '#2d2d2d' : '#ffffff',
                color: darkMode ? '#ffffff' : 'inherit'
              }}>
                <thead>
                  <tr style={{ backgroundColor: darkMode ? '#3d3d3d' : '#f8f9fa' }}>
                    <th style={{ padding: '10px', border: '1px solid ' + (darkMode ? '#555' : '#dee2e6'), textAlign: 'left' }}>Employee</th>
                    <th style={{ padding: '10px', border: '1px solid ' + (darkMode ? '#555' : '#dee2e6'), textAlign: 'left' }}>Date</th>
                    <th style={{ padding: '10px', border: '1px solid ' + (darkMode ? '#555' : '#dee2e6'), textAlign: 'left' }}>Hours</th>
                    <th style={{ padding: '10px', border: '1px solid ' + (darkMode ? '#555' : '#dee2e6'), textAlign: 'left' }}>Type</th>
                    <th style={{ padding: '10px', border: '1px solid ' + (darkMode ? '#555' : '#dee2e6'), textAlign: 'left' }}>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {pendingRequests.slice(0, 5).map(request => {
                    const employee = employeeDetails[request.employeeId];
                    return (
                      <tr key={request.id} style={{
                        borderBottom: '1px solid ' + (darkMode ? '#555' : '#dee2e6'),
                        backgroundColor: darkMode ? '#2d2d2d' : 'transparent'
                      }}>
                        <td style={{ padding: '10px', fontWeight: 'bold' }}>
                          {employee?.name || request.employeeId}
                        </td>
                        <td style={{ padding: '10px' }}>{request.date}</td>
                        <td style={{ padding: '10px' }}>{request.requestedHours} hours</td>
                        <td style={{ padding: '10px' }}>
                          {request.type === 'HOLIDAY' ? 'Holiday' : 'Normal'}
                        </td>
                        <td style={{ padding: '10px' }}>
                          {getOTStatusBadge(request.status)}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>

        <div style={{ marginTop: 20, textAlign: 'right' }}>
          <button
            onClick={() => setShowOvertimePanel(false)}
            style={getButtonStyle('secondary')}
          >
            Close
          </button>
        </div>
      </div>
    );
  };

  // Event renderer for FullCalendar
  const renderEventContent = (eventInfo) => {
    const event = eventInfo.event;
    const extendedProps = event.extendedProps;

    let content = eventInfo.timeText ? (
      <>
        <div><b>{eventInfo.event.title}</b></div>
        <div>{eventInfo.timeText}</div>
      </>
    ) : (
      <div><b>{eventInfo.event.title}</b></div>
    );

    // Add employee name for shift events
    if (extendedProps?.type === 'shift' && extendedProps?.employeeName) {
      content = (
        <>
          <div><b>{eventInfo.event.title}</b></div>
          <div style={{ fontSize: '11px', opacity: 0.8 }}>{extendedProps.employeeName}</div>
          {eventInfo.timeText && <div style={{ fontSize: '10px' }}>{eventInfo.timeText}</div>}
        </>
      );
    }

    // Add leave type for leave events
    if (extendedProps?.type === 'leave') {
      content = (
        <>
          <div><b>{eventInfo.event.title}</b></div>
          <div style={{ fontSize: '11px' }}>{extendedProps.leaveType}</div>
          {eventInfo.timeText && <div style={{ fontSize: '10px' }}>{eventInfo.timeText}</div>}
        </>
      );
    }

    return (
      <div style={{
        padding: '2px 4px',
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'center',
        overflow: 'hidden'
      }}>
        {content}
      </div>
    );
  };

  // Handle event click
  const handleEventClick = (clickInfo) => {
    const event = clickInfo.event;
    const extendedProps = event.extendedProps;

    let message = `<b>${event.title}</b><br/>`;

    if (extendedProps?.type === 'shift') {
      message += `
        Employee: ${extendedProps.employeeName}<br/>
        Department: ${extendedProps.department}<br/>
        Shift: ${event.title}<br/>
        Time: ${event.start.toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'})} - ${event.end.toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'})}<br/>
        ${extendedProps.isOnLeave ? '⚠️ Employee is on leave today' : '✓ Available'}
      `;
    } else if (extendedProps?.type === 'leave') {
      message += `
        Employee: ${extendedProps.employeeName}<br/>
        Leave Type: ${extendedProps.leaveType}<br/>
        Date: ${event.start.toLocaleDateString()}
      `;
    } else if (extendedProps?.type === 'ot-coverage') {
      message += `
        Employee: ${extendedProps.employeeName}<br/>
        Hours: ${extendedProps.assignedHours}<br/>
        Status: ${extendedProps.status}<br/>
        Time: ${event.start.toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'})} - ${event.end.toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'})}
      `;
    }

    alert(message.replace(/<br\/>/g, '\n'));
  };

  // Handle event drop (drag and drop)
  const handleEventDrop = async (dropInfo) => {
    const event = dropInfo.event;
    const newResourceId = event.getResources()[0]?.id;

    if (!newResourceId) return;

    try {
      // Update the assignment in backend
      const res = await fetch('http://localhost:8083/shifts/reassign', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          eventId: event.id,
          newEmployeeId: newResourceId,
          startDate: event.start.toISOString(),
          endDate: event.end.toISOString()
        })
      });

      if (res.ok) {
        alert('Shift reassigned successfully!');
        await loadShiftsAndAssignments();
      } else {
        alert('Failed to reassign shift');
        dropInfo.revert();
      }
    } catch (err) {
      console.error('Event drop error:', err);
      alert('Error reassigning shift: ' + err.message);
      dropInfo.revert();
    }
  };

  return (
    <div className={'common-scrollbar-container ' + (darkMode ? 'dark-theme' : 'light-theme')}>
      {/* Theme Toggle Button */}
      <button
        className="theme-toggle"
        onClick={toggleDarkMode}
        title={darkMode ? 'Switch to Light Mode' : 'Switch to Dark Mode'}
      >
        {darkMode ? '☀️' : '🌙'}
      </button>

      <div className="common-scrollbar-content">
        {/* Header Section */}
        <div className="sticky-header full-width">
          <h1 style={{ color: darkMode ? '#ffffff' : '#333', marginBottom: 10, fontSize: '28px', width: '100%' }}>
            🚀 Indian IT Company - Employee Shift Management
            <span style={{ fontSize: '14px', marginLeft: '10px', opacity: 0.8 }}>
              {darkMode ? '🌙 Dark Mode' : '☀️ Light Mode'} | Current Active Shift: {currentActiveShift || 'None'}
            </span>
          </h1>

          {/* Current Date and Time Display */}
          <div style={{
            marginBottom: 15,
            padding: '10px 15px',
            backgroundColor: darkMode ? '#1e3a5f' : '#e3f2fd',
            borderRadius: 8,
            border: '1px solid ' + (darkMode ? '#2d4d7a' : '#90caf9'),
            textAlign: 'center',
            fontWeight: 'bold',
            fontSize: '16px',
            color: darkMode ? '#ffffff' : '#1565c0',
            width: '100%',
            boxSizing: 'border-box'
          }}>
            📅 {currentDateTime}
          </div>

          <div style={{
            marginBottom: 20,
            padding: 15,
            backgroundColor: darkMode ? '#2d2d2d' : '#f8f9fa',
            borderRadius: 8,
            border: '1px solid ' + (darkMode ? '#444' : '#e9ecef'),
            width: '100%',
            boxSizing: 'border-box'
          }}>

          <button
            onClick={reoptimize}
            disabled={isSolving}
            style={{
                ...getButtonStyle('primary', isSolving),
                position: 'relative',
                overflow: 'hidden'
            }}
           >
            {isSolving ? (
                <>
                    <span style={{
                        display: 'inline-block',
                        width: '16px',
                        height: '16px',
                        border: '2px solid #fff',
                        borderTop: '2px solid transparent',
                        borderRadius: '50%',
                        animation: 'spin 1s linear infinite',
                        marginRight: '8px'
                    }}></span>
                    Generating New Shifts...
                </>
            ) : '🎯 Generate New Shifts'}
          </button>

            <button
              onClick={() => setShowLeavePanel(!showLeavePanel)}
              style={getButtonStyle('info')}
            >
              🏖️ Apply Leave
            </button>

            <button
              onClick={openManageLeavesPanel}
              style={getButtonStyle('secondary')}
            >
              📋 Manage Leaves
            </button>

            <button
              onClick={() => setShowTimeClock(!showTimeClock)}
              style={getButtonStyle('primary')}
            >
              ⏰ Time Clock
            </button>

            <button
              onClick={() => setShowConfigPanel(!showConfigPanel)}
              style={getButtonStyle('warning')}
            >
              ⚙️ Configuration
            </button>

            <button
              onClick={() => setShowCompliancePanel(!showCompliancePanel)}
              style={getButtonStyle('danger')}
            >
              ⚠️ Compliance
            </button>

            <button
              onClick={() => setShowOvertimePanel(!showOvertimePanel)}
              style={getButtonStyle('success')}
            >
              💰 Overtime
            </button>

            {/* Notifications Button */}
            <button
              onClick={() => setShowNotificationsPanel(!showNotificationsPanel)}
              className="notification-badge"
              style={{
                ...getButtonStyle('info'),
                position: 'relative'
              }}
            >
              🔔 Notifications
              {unreadNotificationCount > 0 && (
                <span className="notification-count">
                  {unreadNotificationCount}
                </span>
              )}
            </button>
             <button onClick={openAssignmentPanel}>
                     🔧 Manual Shift Assignment
            </button>
            {/* Legend */}
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '10px', marginTop: '10px', width: '100%' }}>
              <div style={{
                padding: '8px 12px',
                backgroundColor: '#4CAF50',
                color: 'white',
                borderRadius: 4,
                fontWeight: 'bold',
                borderLeft: '4px solid #2E7D32'
              }}>
                🌅 Morning (9 AM - 6 PM)
              </div>

              <div style={{
                padding: '8px 12px',
                backgroundColor: '#FF9800',
                color: 'white',
                borderRadius: 4,
                fontWeight: 'bold',
                borderLeft: '4px solid #EF6C00'
              }}>
                🌇 Afternoon (1 PM - 9 PM)
              </div>

              <div style={{
                padding: '8px 12px',
                backgroundColor: '#F44336',
                color: 'white',
                borderRadius: 4,
                fontWeight: 'bold',
                borderLeft: '4px solid #C62828'
              }}>
                🌃 Night (9 PM - 6 AM)
              </div>

              <div style={{
                padding: '8px 12px',
                backgroundColor: '#6c757d',
                color: 'white',
                borderRadius: 4,
                fontWeight: 'bold',
                border: '2px dashed #495057'
              }}>
                🏖️ On Leave
              </div>

              <div style={{
                padding: '8px 12px',
                backgroundColor: '#FFC107',
                color: '#000000',
                borderRadius: 4,
                fontWeight: 'bold',
                border: '2px solid #FF9800'
              }}>
                🔄 OT Coverage
              </div>
            </div>

            {/* Department Legend */}
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px', marginTop: '10px', width: '100%' }}>
              {Object.entries(DEPARTMENT_COLORS).map(([dept, info]) => (
                <div key={dept} style={{
                  padding: '6px 10px',
                  backgroundColor: info.color,
                  color: 'white',
                  borderRadius: 4,
                  fontSize: '12px',
                  fontWeight: 'bold',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '5px'
                }}>
                  <div style={{ width: '10px', height: '10px', backgroundColor: 'white', borderRadius: '2px' }}></div>
                  {info.name}
                </div>
              ))}
            </div>
          </div>

          <div style={{ marginBottom: 15, width: '100%' }}>
            <div style={{ color: darkMode ? '#cccccc' : '#666', fontSize: '14px', marginBottom: 10 }}>
              • <strong>3-Week Schedule | Indian IT Company | Real-time Clock Management | OT Coverage System</strong>
            </div>

            {employeeCount > 0 && (
              <div style={{
                color: darkMode ? '#90caf9' : '#2196F3',
                fontSize: '14px',
                fontWeight: 'bold',
                padding: '8px 12px',
                backgroundColor: darkMode ? '#1e3a5f' : '#E3F2FD',
                borderRadius: 4,
                display: 'inline-block',
                marginRight: 15,
                border: '1px solid ' + (darkMode     ? '#2d4d7a' : '#90caf9')
              }}>
                👥 Employees: {employees.length}
              </div>
            )}

            {leaveCount > 0 && (
              <div style={{
                color: darkMode ? '#ffcc80' : '#856404',
                fontSize: '14px',
                fontWeight: 'bold',
                padding: '8px 12px',
                backgroundColor: darkMode ? '#5c4a1e' : '#fff3cd',
                borderRadius: 4,
                display: 'inline-block',
                marginRight: 15,
                border: '1px solid ' + (darkMode ? '#7a6128' : '#ffeaa7')
              }}>
                🏖️ Leave Days: {leaveCount}
              </div>
            )}

            {complianceViolations.length > 0 && (
              <div style={{
                color: darkMode ? '#ffcccb' : '#721c24',
                fontSize: '14px',
                fontWeight: 'bold',
                padding: '8px 12px',
                backgroundColor: darkMode ? '#5c1a1a' : '#f8d7da',
                borderRadius: 4,
                display: 'inline-block',
                marginRight: 15,
                border: '1px solid ' + (darkMode ? '#7a2828' : '#f5c6cb')
              }}>
                ⚠️ Violations: {complianceViolations.filter(v => !v.resolved).length}
              </div>
            )}

            {/* OT Requests Count */}
            {otRequests.filter(r => r.status === 'PENDING').length > 0 && (
              <div style={{
                color: darkMode ? '#ffffff' : '#856404',
                fontSize: '14px',
                fontWeight: 'bold',
                padding: '8px 12px',
                backgroundColor: darkMode ? '#FF9800' : '#FF9800',
                borderRadius: 4,
                display: 'inline-block',
                marginRight: 15,
                border: '1px solid ' + (darkMode ? '#EF6C00' : '#EF6C00')
              }}>
                ⏳ Pending OT: {otRequests.filter(r => r.status === 'PENDING').length}
              </div>
            )}

            {/* Coverage Requests Count */}
            {leaveCoverageRequests.filter(r => r.status === 'PENDING').length > 0 && (
              <div style={{
                color: darkMode ? '#ffffff' : '#0c5460',
                fontSize: '14px',
                fontWeight: 'bold',
                padding: '8px 12px',
                backgroundColor: darkMode ? '#17a2b8' : '#17a2b8',
                borderRadius: 4,
                display: 'inline-block',
                border: '1px solid ' + (darkMode ? '#0c5460' : '#0c5460')
              }}>
                🔄 Pending Coverage: {leaveCoverageRequests.filter(r => r.status === 'PENDING').length}
              </div>
            )}

            {/* Unread Notifications */}
            {unreadNotificationCount > 0 && (
              <div style={{
                color: '#ffffff',
                fontSize: '14px',
                fontWeight: 'bold',
                padding: '8px 12px',
                backgroundColor: '#f44336',
                borderRadius: 4,
                display: 'inline-block',
                marginLeft: '15px',
                border: '1px solid #c62828',
                animation: 'pulse 2s infinite'
              }}>
                🔔 Unread: {unreadNotificationCount}
              </div>
            )}
          </div>
        </div>

        {/* Configuration Panel */}
        {showConfigPanel && renderConfigPanel()}

        {/* Notifications Panel */}
        {showNotificationsPanel && renderNotificationsPanel()}

        {/* Leave Coverage Panel */}
        {showLeaveCoveragePanel && renderLeaveCoveragePanel()}

        {/* OT Request Panel */}
        {showOTRequestPanel && renderOTRequestPanel()}

        {/* OT Management Panel */}
        {showOTManagementPanel && renderOTManagementPanel()}

        {/* Compliance Panel */}
        {showCompliancePanel && renderCompliancePanel()}

        {/* Overtime Panel */}
        {showOvertimePanel && !showOTRequestPanel && !showOTManagementPanel && !showLeaveCoveragePanel && renderOvertimePanel()}

        {/* Leave Application Panel */}
        {showLeavePanel && (
          <div className="auto-height-panel full-width" style={{
            marginBottom: 20,
            padding: 20,
            backgroundColor: darkMode ? '#1e3a5f' : '#e7f3ff',
            borderRadius: 8,
            border: '2px solid ' + (darkMode ? '#2d4d7a' : '#bee5eb'),
            color: darkMode ? '#ffffff' : '#0c5460',
            position: 'relative',
            zIndex: 100
          }}>
            <h3 style={{ marginTop: 0 }}>Apply Leave</h3>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(250px, 1fr))', gap: 15, width: '100%' }}>
              <div style={{ position: 'relative', zIndex: 1000 }}>
                <label>Employee:</label>
                <div style={{ position: 'relative' }}>
                  <select
                    value={selectedEmployee}
                    onChange={(e) => {
                      e.stopPropagation();
                      setSelectedEmployee(e.target.value);
                    }}
                    onClick={(e) => e.stopPropagation()}
                    onMouseDown={(e) => e.stopPropagation()}
                    style={{
                      ...getInputStyle(),
                      WebkitAppearance: 'menulist',
                      MozAppearance: 'menulist',
                      appearance: 'menulist',
                      position: 'relative',
                      zIndex: 1001
                    }}
                    id="leave-employee-select"
                  >
                    <option value="">Select Employee</option>
                    {Object.values(employeeDetails).map(emp => (
                      <option key={emp.id} value={emp.id}>
                        {emp.name} ({emp.department})
                        {emp.annualLeaveBalance !== undefined &&
                          ` - Leave: A:${emp.annualLeaveBalance} S:${emp.sickLeaveBalance} C:${emp.casualLeaveBalance}`
                        }
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              <div>
                <label>Leave Type:</label>
                <select
                  value={leaveType}
                  onChange={(e) => setLeaveType(e.target.value)}
                  onClick={(e) => e.stopPropagation()}
                  onMouseDown={(e) => e.stopPropagation()}
                  style={{
                    ...getInputStyle(),
                    WebkitAppearance: 'menulist',
                    MozAppearance: 'menulist',
                    appearance: 'menulist'
                  }}
                >
                  <option value="ANNUAL">Annual Leave</option>
                  <option value="SICK">Sick Leave</option>
                  <option value="CASUAL">Casual Leave</option>
                </select>
              </div>

              <div>
                <label>Start Date:</label>
                <input
                  type="date"
                  value={leaveStartDate}
                  onChange={(e) => setLeaveStartDate(e.target.value)}
                  onClick={(e) => e.stopPropagation()}
                  style={getInputStyle()}
                />
              </div>

              <div>
                <label>End Date:</label>
                <input
                  type="date"
                  value={leaveEndDate}
                  onChange={(e) => setLeaveEndDate(e.target.value)}
                  onClick={(e) => e.stopPropagation()}
                  style={getInputStyle()}
                />
              </div>

              <div style={{ display: 'flex', alignItems: 'flex-end', gap: '10px', gridColumn: '1 / -1' }}>
                <button
                  onClick={applyLeave}
                  disabled={!selectedEmployee || !leaveStartDate || !leaveEndDate}
                  style={getButtonStyle('info', !selectedEmployee || !leaveStartDate || !leaveEndDate)}
                >
                  Apply Leave
                </button>

                <button
                  onClick={() => setShowLeavePanel(false)}
                  style={getButtonStyle('secondary')}
                >
                  Cancel
                </button>
              </div>
            </div>
          </div>
        )}

        {showDirectCoveragePanel && (
          <div className="auto-height-panel full-width" style={{
            marginBottom: 20,
            padding: 20,
            backgroundColor: darkMode ? '#5c4a1e' : '#fff3cd',
            borderRadius: 8,
            border: '2px solid ' + (darkMode ? '#7a6128' : '#ffeaa7'),
            color: darkMode ? '#ffffff' : '#856404'
          }}>
            <h3 style={{ marginTop: 0 }}>🔄 Direct Leave Coverage Assignment</h3>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(250px, 1fr))', gap: 15, marginBottom: 20 }}>
              <div>
                <label>Employee on Leave:</label>
                <select
                  value={directCoverageEmployee}
                  onChange={(e) => setDirectCoverageEmployee(e.target.value)}
                  style={getInputStyle()}
                >
                  <option value="">Select Employee</option>
                  {Array.from(employeeLeaves.entries()).map(([empId, leaveDates]) => {
                    const emp = employees.find(e => e.id === empId) || employeeDetails[empId];
                    if (!emp) return null;

                    const leaveCount = leaveDates.size;
                    const firstLeaveDate = Array.from(leaveDates)[0];
                    return (
                      <option key={empId} value={empId}>
                        {emp.title || emp.name} ({emp.department}) - {leaveCount} leave{leaveCount !== 1 ? 's' : ''}
                      </option>
                    );
                  })}
                </select>

                {/* Show selected employee's leave dates */}
                {directCoverageEmployee && employeeLeaves.has(directCoverageEmployee) && (
                  <div style={{ marginTop: '10px' }}>
                    <div style={{ fontSize: '12px', color: '#4CAF50', marginBottom: '5px' }}>
                      📅 Leave Dates:
                    </div>
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: '5px' }}>
                      {Array.from(employeeLeaves.get(directCoverageEmployee)).map(date => (
                        <span
                          key={date}
                          onClick={() => setDirectCoverageDate(date)}
                          style={{
                            padding: '4px 8px',
                            backgroundColor: directCoverageDate === date ? '#4CAF50' : '#e9ecef',
                            color: directCoverageDate === date ? 'white' : '#333',
                            borderRadius: '4px',
                            fontSize: '11px',
                            cursor: 'pointer',
                            border: `1px solid ${directCoverageDate === date ? '#2E7D32' : '#ccc'}`
                          }}
                        >
                          {date}
                        </span>
                      ))}
                    </div>
                  </div>
                )}
              </div>

              <div>
                <label>Leave Date:</label>
                <input
                  type="date"
                  value={directCoverageDate}
                  onChange={(e) => setDirectCoverageDate(e.target.value)}
                  style={getInputStyle()}
                />
              </div>

              <div style={{ display: 'flex', alignItems: 'flex-end' }}>
                <button
                  onClick={() => loadDirectCoverageOptions(directCoverageEmployee, directCoverageDate)}
                  disabled={!directCoverageEmployee || !directCoverageDate}
                  style={getButtonStyle('info', !directCoverageEmployee || !directCoverageDate)}
                >
                  👥 Find Suitable Employees
                </button>
              </div>
            </div>

            {/* Suitable Employees List */}
            {directSuitableEmployees.length > 0 && (
              <div style={{
                marginBottom: 20,
                padding: 15,
                backgroundColor: darkMode ? '#2d2d2d' : '#f8f9fa',
                borderRadius: 6,
                border: '1px solid ' + (darkMode ? '#444' : '#e9ecef')
              }}>
                <h4>👥 Suitable Employees ({directSuitableEmployees.length} found)</h4>

                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))', gap: 10, marginTop: 10 }}>
                  {directSuitableEmployees.map(emp => {
                    const otWage = emp.otWages ? emp.otWages[`${directCoverageHours}h`] : 0;

                    return (
                      <div key={emp.id} style={{
                        padding: 15,
                        backgroundColor: darkMode ? '#3d3d3d' : '#ffffff',
                        borderRadius: '6px',
                        border: '2px solid ' + (directSelectedEmployee === emp.id ? '#4CAF50' : '#e9ecef'),
                        cursor: 'pointer',
                        transition: 'all 0.3s ease'
                      }} onClick={() => setDirectSelectedEmployee(emp.id)}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                          <div style={{ fontWeight: 'bold' }}>{emp.name}</div>
                          <div style={{
                            padding: '2px 8px',
                            backgroundColor: directSelectedEmployee === emp.id ? '#4CAF50' : '#6c757d',
                            color: 'white',
                            borderRadius: '4px',
                            fontSize: '12px'
                          }}>
                            {emp.skillMatch}
                          </div>
                        </div>

                        <div style={{ fontSize: '14px', color: darkMode ? '#cccccc' : '#666', marginTop: 5 }}>
                          {emp.department} • {emp.position}
                        </div>

                        <div style={{ marginTop: 10 }}>
                          <div style={{ fontSize: '12px', color: darkMode ? '#999' : '#777' }}>Skills:</div>
                          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 5, marginTop: 5 }}>
                            {emp.skills.slice(0, 3).map((skill, idx) => (
                              <span key={idx} style={{
                                padding: '2px 6px',
                                backgroundColor: darkMode ? '#4CAF50' : '#4CAF50',
                                color: 'white',
                                borderRadius: '12px',
                                fontSize: '11px'
                              }}>
                                {skill}
                              </span>
                            ))}
                          </div>
                        </div>

                        <div style={{ marginTop: 10, display: 'flex', justifyContent: 'space-between' }}>
                          <div>
                            <div style={{ fontSize: '12px', color: darkMode ? '#999' : '#777' }}>Hourly Wage:</div>
                            <div style={{ fontWeight: 'bold' }}>${emp.hourlyWage}/hr</div>
                          </div>
                          <div>
                            <div style={{ fontSize: '12px', color: darkMode ? '#999' : '#777' }}>OT Pay ({directCoverageHours}h):</div>
                            <div style={{ fontWeight: 'bold', color: '#FF9800' }}>
                              ${otWage ? otWage.toFixed(2) : '0.00'}
                            </div>
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            )}

            {/* Assignment Controls */}
            {directSelectedEmployee && (
              <div style={{
                padding: 15,
                backgroundColor: darkMode ? '#1e3a5f' : '#e3f2fd',
                borderRadius: 6,
                marginBottom: 15,
                border: '1px solid ' + (darkMode ? '#2d4d7a' : '#90caf9')
              }}>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 15 }}>
                  <div>
                    <label>OT Hours:</label>
                    <div style={{ display: 'flex', gap: 10, marginTop: 5 }}>
                      {[1, 2, 3].map(hours => (
                        <button
                          key={hours}
                          onClick={() => setDirectCoverageHours(hours.toString())}
                          style={{
                            flex: 1,
                            padding: '10px',
                            backgroundColor: directCoverageHours === hours.toString() ?
                              (darkMode ? '#4CAF50' : '#4CAF50') :
                              (darkMode ? '#444' : '#e9ecef'),
                            color: directCoverageHours === hours.toString() ? 'white' :
                              (darkMode ? '#ffffff' : '#333'),
                            border: '2px solid ' + (directCoverageHours === hours.toString() ? '#2E7D32' : '#ccc'),
                            borderRadius: '6px',
                            cursor: 'pointer',
                            fontWeight: 'bold',
                            fontSize: '14px'
                          }}
                        >
                          {hours}h
                        </button>
                      ))}
                    </div>
                  </div>

                  <div>
                    <label>Coverage Type:</label>
                    <select
                      value={directCoverageType}
                      onChange={(e) => setDirectCoverageType(e.target.value)}
                      style={getInputStyle()}
                    >
                      <option value="COVERAGE">Regular Coverage (1.2x OT)</option>
                      <option value="EMERGENCY">Emergency Coverage (1.5x OT)</option>
                      <option value="HOLIDAY">Holiday Coverage (2.0x OT)</option>
                    </select>
                  </div>

                  <div style={{ display: 'flex', alignItems: 'flex-end' }}>
                    <button
                      onClick={quickAssignCoverage}
                      style={{
                        ...getButtonStyle('primary'),
                        padding: '12px 24px',
                        fontSize: '16px'
                      }}
                    >
                      ✅ Assign Coverage
                    </button>
                  </div>
                </div>
              </div>
            )}

            <div style={{ textAlign: 'right' }}>
              <button
                onClick={() => {
                  setShowDirectCoveragePanel(false);
                  setDirectCoverageEmployee('');
                  setDirectCoverageDate('');
                  setDirectSelectedEmployee('');
                  setDirectSuitableEmployees([]);
                }}
                style={getButtonStyle('secondary')}
              >
                ✕ Close
              </button>
            </div>
          </div>
        )}

        {/* Manage Leaves Panel */}
        {showManageLeavesPanel && (
          <div className="auto-height-panel full-width" style={{
            marginBottom: 20,
            padding: 20,
            backgroundColor: darkMode ? '#5c4a1e' : '#fff3cd',
            borderRadius: 8,
            border: '2px solid ' + (darkMode ? '#7a6128' : '#ffeaa7'),
            color: darkMode ? '#ffffff' : '#856404'
          }}>
            <h3 style={{ marginTop: 0 }}>📋 Manage Leaves ({currentLeaves.length} leaves)</h3>

            {loadingLeaves ? (
              <div style={{ textAlign: 'center', padding: '20px', width: '100%' }}>
                <div className="loading-spinner"></div>
                <div>Loading leaves...</div>
              </div>
            ) : currentLeaves.length === 0 ? (
              <div style={{ textAlign: 'center', padding: '40px', width: '100%', backgroundColor: darkMode ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.03)', borderRadius: '6px' }}>
                <div style={{ fontSize: '48px', marginBottom: '20px' }}>📋</div>
                <div style={{ fontSize: '16px', fontWeight: 'bold', color: darkMode ? '#cccccc' : '#666' }}>
                  No leaves currently applied
                </div>
                <div style={{ fontSize: '14px', opacity: 0.8, marginTop: '10px' }}>
                  Apply leaves using the "Apply Leave" button above
                </div>
              </div>
            ) : (
              <div className="no-individual-scroll full-width">
                <table style={{
                  width: '100%',
                  borderCollapse: 'collapse',
                  backgroundColor: darkMode ? '#2d2d2d' : '#ffffff',
                  color: darkMode ? '#ffffff' : 'inherit',
                  borderRadius: '6px',
                  overflow: 'hidden'
                }}>
                  <thead>
                    <tr style={{
                      backgroundColor: darkMode ? '#3d3d3d' : '#f8f9fa',
                      borderBottom: '2px solid ' + (darkMode ? '#555' : '#dee2e6')
                    }}>
                      <th style={{
                        padding: '12px 15px',
                        textAlign: 'left',
                        fontWeight: 'bold',
                        color: darkMode ? '#ffffff' : '#333'
                      }}>Employee Information</th>
                      <th style={{
                        padding: '12px 15px',
                        textAlign: 'left',
                        fontWeight: 'bold',
                        color: darkMode ? '#ffffff' : '#333'
                      }}>Leave Details</th>
                      <th style={{
                        padding: '12px 15px',
                        textAlign: 'center',
                        fontWeight: 'bold',
                        color: darkMode ? '#ffffff' : '#333',
                        width: '200px'
                      }}>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {currentLeaves.map((leave, index) => {
                      const empDetails = employeeDetails[leave.employeeId];
                      const leaveColor = getLeaveColor(leave.leaveType || 'ANNUAL');

                      return (
                        <tr key={leave.id || index} style={{
                          borderBottom: '1px solid ' + (darkMode ? '#444' : '#e9ecef'),
                          backgroundColor: index % 2 === 0 ?
                            (darkMode ? '#2d2d2d' : '#ffffff') :
                            (darkMode ? '#3d3d3d' : '#f8f9fa'),
                          transition: 'background-color 0.3s ease'
                        }}>
                          <td style={{ padding: '15px' }}>
                            {empDetails ? (
                              <div>
                                <div style={{
                                  display: 'flex',
                                  alignItems: 'center',
                                  gap: '12px',
                                  marginBottom: '8px'
                                }}>
                                  <div style={{
                                    width: '40px',
                                    height: '40px',
                                    backgroundColor: empDetails.shiftColor,
                                    borderRadius: '50%',
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    color: 'white',
                                    fontWeight: 'bold',
                                    fontSize: '16px'
                                  }}>
                                    {empDetails.name.charAt(0)}
                                  </div>
                                  <div>
                                    <div style={{
                                      fontWeight: 'bold',
                                      fontSize: '16px',
                                      color: darkMode ? '#ffffff' : '#333'
                                    }}>
                                      {empDetails.name}
                                    </div>
                                    <div style={{
                                      fontSize: '14px',
                                      color: darkMode ? '#cccccc' : '#666',
                                      marginTop: '4px'
                                    }}>
                                      {empDetails.department} • {empDetails.position}
                                    </div>
                                  </div>
                                </div>
                                <div style={{
                                  fontSize: '13px',
                                  color: darkMode ? '#999' : '#777',
                                  marginTop: '8px'
                                }}>
                                  <div>ID: {empDetails.id}</div>
                                  <div>Leave Balance:
                                    <span style={{ color: '#4CAF50', fontWeight: 'bold', marginLeft: '5px' }}>
                                      A:{empDetails.annualLeaveBalance}
                                    </span>
                                    <span style={{ color: '#2196F3', margin: '0 5px' }}>
                                      S:{empDetails.sickLeaveBalance}
                                    </span>
                                    <span style={{ color: '#FF9800' }}>
                                      C:{empDetails.casualLeaveBalance}
                                    </span>
                                  </div>
                                </div>
                              </div>
                            ) : (
                              <div style={{ color: '#F44336', fontWeight: 'bold' }}>
                                Employee not found: {leave.employeeId}
                              </div>
                            )}
                          </td>
                          <td style={{ padding: '15px' }}>
                            <div style={{
                              display: 'flex',
                              flexDirection: 'column',
                              gap: '10px'
                            }}>
                              <div>
                                <div style={{
                                  fontSize: '14px',
                                  color: darkMode ? '#cccccc' : '#666',
                                  marginBottom: '5px'
                                }}>
                                  Leave Date
                                </div>
                                <div style={{
                                  fontSize: '18px',
                                  fontWeight: 'bold',
                                  color: leaveColor
                                }}>
                                  {leave.leaveDate}
                                </div>
                              </div>
                              <div>
                                <div style={{
                                  fontSize: '14px',
                                  color: darkMode ? '#cccccc' : '#666',
                                  marginBottom: '5px'
                                }}>
                                  Leave Type
                                </div>
                                <div style={{
                                  display: 'inline-block',
                                  padding: '4px 12px',
                                  backgroundColor: leaveColor + '20',
                                  borderRadius: '20px',
                                  fontSize: '14px',
                                  fontWeight: 'bold',
                                  color: leaveColor,
                                  border: `1px solid ${leaveColor}`
                                }}>
                                  {leave.leaveType}
                                </div>
                              </div>
                            </div>
                          </td>
                          <td style={{ padding: '15px', textAlign: 'center' }}>
                            <div style={{
                              display: 'flex',
                              flexDirection: 'column',
                              gap: '10px',
                              alignItems: 'center'
                            }}>
                              <button
                                onClick={() => revokeLeave(leave.employeeId, leave.leaveDate, leave.leaveType)}
                                style={{
                                  padding: '10px 20px',
                                  backgroundColor: '#F44336',
                                  color: 'white',
                                  border: 'none',
                                  borderRadius: '6px',
                                  fontWeight: 'bold',
                                  cursor: 'pointer',
                                  transition: 'all 0.3s ease',
                                  width: '180px'
                                }}
                                onMouseEnter={(e) => {
                                  e.target.style.transform = 'translateY(-2px)';
                                  e.target.style.boxShadow = '0 4px 12px rgba(244, 67, 54, 0.3)';
                                }}
                                onMouseLeave={(e) => {
                                  e.target.style.transform = 'translateY(0)';
                                  e.target.style.boxShadow = 'none';
                                }}
                              >
                                ❌ Revoke This Leave
                              </button>
                              <button
                                onClick={() => removeAllLeaves(leave.employeeId)}
                                style={{
                                  padding: '10px 20px',
                                  backgroundColor: '#FF9800',
                                  color: 'white',
                                  border: 'none',
                                  borderRadius: '6px',
                                  fontWeight: 'bold',
                                  cursor: 'pointer',
                                  transition: 'all 0.3s ease',
                                  width: '180px'
                                }}
                                onMouseEnter={(e) => {
                                  e.target.style.transform = 'translateY(-2px)';
                                  e.target.style.boxShadow = '0 4px 12px rgba(255, 152, 0, 0.3)';
                                }}
                                onMouseLeave={(e) => {
                                  e.target.style.transform = 'translateY(0)';
                                  e.target.style.boxShadow = 'none';
                                }}
                              >
                                🗑️ Remove All Leaves
                              </button>
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}

            <div style={{
              marginTop: '20px',
              textAlign: 'right',
              width: '100%',
              paddingTop: '15px',
              borderTop: '1px solid ' + (darkMode ? '#555' : '#e9ecef')
            }}>
              <button
                onClick={() => setShowManageLeavesPanel(false)}
                style={getButtonStyle('secondary')}
              >
                ✕ Close Manage Leaves
              </button>
            </div>
          </div>
        )}

        {/* Time Clock Panel */}
        {showTimeClock && (
          <div className="auto-height-panel full-width" style={{
            marginBottom: 20,
            padding: 20,
            backgroundColor: darkMode ? '#1a472a' : '#e8f5e8',
            borderRadius: 8,
            border: '2px solid ' + (darkMode ? '#2d5a3a' : '#4caf50'),
            color: darkMode ? '#ffffff' : '#2e7d32',
            width: '100%'
          }}>
            <div style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              marginBottom: 15,
              width: '100%'
            }}>
              <h3 style={{ marginTop: 0 }}>⏰ Time Clock - Current Active Shift: {currentActiveShift || 'None'}</h3>
              <div style={{
                padding: '8px 12px',
                backgroundColor: darkMode ? '#2d5a3a' : '#2e7d32',
                color: 'white',
                borderRadius: 4,
                fontWeight: 'bold',
                fontSize: '14px'
              }}>
                🕒 {currentDateTime}
              </div>
            </div>

            {/* Available Employees Section */}
            <div style={{
              padding: '15px',
              backgroundColor: darkMode ? '#1e3a5f' : '#e7f3ff',
              borderRadius: '6px',
              marginBottom: '20px',
              border: '2px solid ' + (darkMode ? '#2d4d7a' : '#bee5eb'),
              width: '100%',
              color: darkMode ? '#ffffff' : '#0c5460'
            }}>
              <div style={{
                fontWeight: 'bold',
                fontSize: '16px',
                marginBottom: '10px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between'
              }}>
                <div>
                  👥 Available for Clock-in ({availableEmployees.length} employees)
                </div>
                <div style={{ fontSize: '12px', opacity: 0.8 }}>
                  <span style={{ color: '#4CAF50', fontWeight: 'bold' }}>● AVAILABLE</span> •
                  <span style={{ color: '#FF9800', marginLeft: '10px' }}>⚠️ LATE</span> •
                  <span style={{ color: '#F44336', marginLeft: '10px' }}>⛔ UNAVAILABLE</span>
                </div>
              </div>

              {availableEmployees.length === 0 ? (
                <div style={{
                  textAlign: 'center',
                  padding: '40px 20px',
                  backgroundColor: darkMode ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.03)',
                  borderRadius: '6px',
                  width: '100%',
                  color: darkMode ? '#cccccc' : '#666'
                }}>
                  <div style={{ fontSize: '48px', marginBottom: '20px' }}>⏰</div>
                  <div style={{ fontSize: '16px', fontWeight: 'bold', marginBottom: '10px' }}>
                    No employees available for clock-in right now
                  </div>
                  <div style={{ fontSize: '14px', opacity: 0.8 }}>
                    Employees become available 30 minutes before their shift starts
                  </div>
                </div>
              ) : (
                <div style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))',
                  gap: '15px',
                  width: '100%'
                }}>
                  {availableEmployees.map((emp) => {
                    const shiftConfig = SHIFT_CONFIG[emp.shift];
                    const isOnLeave = isEmployeeOnLeaveToday(emp.id, scheduleData);
                    const canClockIn = emp.isShiftActive && !isOnLeave;
                    const empDetails = employeeDetails[emp.id];
                    const isClockedIn = getEmployeeClockStatus(emp.id);

                    // Determine card color based on status
                    let cardColor = shiftConfig?.color || '#607D8B';
                    let borderColor = emp.employeeColor || '#607D8B';
                    let statusText = 'AVAILABLE';
                    let statusColor = '#4CAF50';

                    if (isClockedIn) {
                      cardColor = '#4CAF50';
                      borderColor = '#2E7D32';
                      statusText = 'CLOCKED IN';
                      statusColor = '#2E7D32';
                    } else if (!canClockIn) {
                      cardColor = '#F44336';
                      borderColor = '#C62828';
                      statusText = 'UNAVAILABLE';
                      statusColor = '#C62828';
                    } else if (emp.lateMinutes > 0) {
                      cardColor = '#FF9800';
                      borderColor = '#EF6C00';
                      statusText = 'LATE';
                      statusColor = '#EF6C00';
                    }

                    return (
                      <div key={emp.id} style={{
                        padding: '15px',
                        backgroundColor: cardColor,
                        color: 'white',
                        borderRadius: '8px',
                        boxShadow: '0 4px 8px rgba(0,0,0,0.1)',
                        borderLeft: '6px solid ' + borderColor,
                        opacity: canClockIn || isClockedIn ? 1 : 0.6,
                        position: 'relative',
                        overflow: 'hidden'
                      }}>
                        {/* Status indicator bar */}
                        <div style={{
                          position: 'absolute',
                          top: 0,
                          left: 0,
                          right: 0,
                          height: '4px',
                          backgroundColor: statusColor
                        }}></div>

                        {/* Employee Header */}
                        <div style={{
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'flex-start',
                          marginBottom: '10px',
                          position: 'relative',
                          zIndex: 1
                        }}>
                          <div style={{ flex: 1 }}>
                            <div style={{ fontWeight: 'bold', fontSize: '16px' }}>{emp.name}</div>
                            <div style={{ fontSize: '12px', opacity: 0.9, marginTop: '5px' }}>
                              {emp.department} • {emp.position}
                            </div>
                          </div>
                          <div style={{
                            padding: '4px 8px',
                            backgroundColor: 'rgba(255,255,255,0.2)',
                            borderRadius: '4px',
                            fontSize: '12px',
                            fontWeight: 'bold',
                            border: '1px solid rgba(255,255,255,0.3)'
                          }}>
                            {statusText}
                          </div>
                        </div>

                        {/* Shift Information */}
                        <div style={{
                          backgroundColor: 'rgba(255,255,255,0.1)',
                          padding: '10px',
                          borderRadius: '6px',
                          marginBottom: '10px',
                          position: 'relative',
                          zIndex: 1
                        }}>
                          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '5px' }}>
                            <div>
                              <div style={{ fontSize: '14px', fontWeight: 'bold' }}>
                                {emp.shift} Shift
                              </div>
                              <div style={{ fontSize: '12px', opacity: 0.9 }}>
                                {emp.shiftStart} - {emp.shiftEnd}
                              </div>
                            </div>
                            <div style={{
                              width: '20px',
                              height: '20px',
                              backgroundColor: shiftConfig?.color || '#607D8B',
                              borderRadius: '50%',
                              border: '2px solid white'
                            }}></div>
                          </div>

                          {/* Late indicator */}
                          {emp.lateMinutes > 0 && !isClockedIn && (
                            <div style={{
                              marginTop: '8px',
                              padding: '4px 8px',
                              backgroundColor: 'rgba(255,255,255,0.2)',
                              borderRadius: '4px',
                              fontSize: '11px',
                              display: 'flex',
                              alignItems: 'center',
                              gap: '5px'
                            }}>
                              <span>⚠️</span>
                              <span>Late by: {formatLateTime(emp.lateMinutes)}</span>
                            </div>
                          )}
                        </div>

                        {/* Employee Details */}
                        <div style={{
                          fontSize: '12px',
                          opacity: 0.8,
                          marginBottom: '15px',
                          position: 'relative',
                          zIndex: 1
                        }}>
                          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
                            <div>
                              <div style={{ fontWeight: 'bold' }}>Hourly Wage</div>
                              <div>${emp.hourlyWage}/hr</div>
                            </div>
                            <div>
                              <div style={{ fontWeight: 'bold' }}>Category</div>
                              <div>{emp.category}</div>
                            </div>
                            <div>
                              <div style={{ fontWeight: 'bold' }}>Gender</div>
                              <div>{emp.gender}</div>
                            </div>
                            <div>
                              <div style={{ fontWeight: 'bold' }}>Status</div>
                              <div style={{
                                color: emp.lateMinutes > 0 ? '#FF9800' : '#4CAF50',
                                fontWeight: 'bold',
                                display: 'flex',
                                alignItems: 'center',
                                gap: '3px'
                              }}>
                                {emp.lateMinutes > 0 ? '⚠️ ' : '✓ '}
                                {emp.lateMinutes > 0 ? emp.lateMinutes + 'm late' : 'On Time'}
                              </div>
                            </div>
                          </div>
                        </div>

                        {/* Action Button */}
                        {isClockedIn ? (
                          <div style={{
                            display: 'flex',
                            gap: '10px',
                            position: 'relative',
                            zIndex: 1
                          }}>
                            <button
                              onClick={() => handleClockOut(emp.id)}
                              style={{
                                flex: 1,
                                padding: '10px',
                                backgroundColor: '#F44336',
                                color: 'white',
                                border: 'none',
                                borderRadius: '6px',
                                fontWeight: 'bold',
                                fontSize: '14px',
                                cursor: 'pointer',
                                transition: 'all 0.3s ease',
                                display: 'flex',
                                justifyContent: 'center',
                                alignItems: 'center',
                                gap: '8px'
                              }}
                              onMouseEnter={(e) => {
                                e.target.style.transform = 'translateY(-2px)';
                                e.target.style.boxShadow = '0 6px 12px rgba(0,0,0,0.2)';
                              }}
                              onMouseLeave={(e) => {
                                e.target.style.transform = 'translateY(0)';
                                e.target.style.boxShadow = 'none';
                              }}
                            >
                              <span style={{ fontSize: '16px' }}>⏰</span>
                              Clock Out
                            </button>
                            <button
                              onClick={() => handleBreakStart(emp.id)}
                              style={{
                                padding: '10px',
                                backgroundColor: '#FF9800',
                                color: 'white',
                                border: 'none',
                                borderRadius: '6px',
                                fontWeight: 'bold',
                                fontSize: '14px',
                                cursor: 'pointer',
                                transition: 'all 0.3s ease',
                                minWidth: '40px'
                              }}
                            >
                              ⏸️
                            </button>
                          </div>
                        ) : (
                          <button
                            onClick={() => {
                              // Clear previous selection
                              setTimeClockSelectedEmployee('');

                              // Set the correct employee ID
                              setTimeout(() => {
                                setTimeClockSelectedEmployee(emp.id);
                                handleClockInForEmployee(emp.id);
                              }, 50);
                            }}
                            disabled={!canClockIn}
                            style={{
                              width: '100%',
                              padding: '12px',
                              backgroundColor: canClockIn ?
                                (emp.lateMinutes > 0 ? '#FF9800' : '#28a745') :
                                '#6c757d',
                              color: 'white',
                              border: 'none',
                              borderRadius: '6px',
                              fontWeight: 'bold',
                              fontSize: '14px',
                              cursor: canClockIn ? 'pointer' : 'not-allowed',
                              opacity: canClockIn ? 1 : 0.6,
                              transition: 'all 0.3s ease',
                              position: 'relative',
                              zIndex: 1,
                              display: 'flex',
                              justifyContent: 'center',
                              alignItems: 'center',
                              gap: '8px'
                            }}
                          >
                            <span style={{ fontSize: '18px' }}>🔔</span>
                            {canClockIn ? (
                              emp.lateMinutes > 0 ?
                                'Clock In (' + emp.lateMinutes + 'm late)' :
                                'Clock In Now'
                            ) : (
                              'Not Available'
                            )}
                          </button>
                        )}
                      </div>
                    );
                  })}
                </div>
              )}
            </div>

            {/* Today's Attendance Records */}
            <div style={{
              padding: '15px',
              backgroundColor: darkMode ? '#2d2d2d' : '#f8f9fa',
              borderRadius: '6px',
              border: '2px solid ' + (darkMode ? '#444' : '#e9ecef'),
              width: '100%'
            }}>
              <div style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                marginBottom: '15px',
                width: '100%'
              }}>
                <h4 style={{ margin: 0, color: darkMode ? '#ffffff' : 'inherit' }}>
                  📊 Today's Attendance Records
                </h4>
                <div style={{
                  fontSize: '14px',
                  color: darkMode ? '#cccccc' : '#666',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '10px'
                }}>
                  <span>Total: {todayAttendance.length} records</span>
                  <span style={{
                    padding: '4px 8px',
                    backgroundColor: darkMode ? '#2d5a3a' : '#4CAF50',
                    color: 'white',
                    borderRadius: '4px',
                    fontSize: '12px'
                  }}>
                    {new Date().toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' })}
                  </span>
                </div>
              </div>

              {todayAttendance.length === 0 ? (
                <div style={{
                  textAlign: 'center',
                  padding: '40px 20px',
                  backgroundColor: darkMode ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.03)',
                  borderRadius: '4px',
                  width: '100%',
                  color: darkMode ? '#cccccc' : '#666'
                }}>
                  <div style={{ fontSize: '48px', marginBottom: '20px' }}>📊</div>
                  <div style={{ fontSize: '16px', fontWeight: 'bold', marginBottom: '10px' }}>
                    No attendance records for today
                  </div>
                  <div style={{ fontSize: '14px', opacity: 0.8 }}>
                    Clock-in employees to see attendance records here
                  </div>
                </div>
              ) : (
                <div className="no-individual-scroll full-width">
                  <table style={{
                    width: '100%',
                    borderCollapse: 'collapse',
                    backgroundColor: darkMode ? '#2d2d2d' : '#ffffff',
                    color: darkMode ? '#ffffff' : 'inherit',
                    borderRadius: '6px',
                    overflow: 'hidden'
                  }}>
                    <thead>
                      <tr style={{
                        backgroundColor: darkMode ? '#3d3d3d' : '#4CAF50',
                        color: 'white'
                      }}>
                        <th style={{
                          padding: '12px',
                          textAlign: 'left',
                          fontWeight: 'bold',
                          fontSize: '14px'
                        }}>Employee</th>
                        <th style={{
                          padding: '12px',
                          textAlign: 'left',
                          fontWeight: 'bold',
                          fontSize: '14px'
                        }}>Shift</th>
                        <th style={{
                          padding: '12px',
                          textAlign: 'left',
                          fontWeight: 'bold',
                          fontSize: '14px'
                        }}>Clock In</th>
                        <th style={{
                          padding: '12px',
                          textAlign: 'left',
                          fontWeight: 'bold',
                          fontSize: '14px'
                        }}>Clock Out</th>
                        <th style={{
                          padding: '12px',
                          textAlign: 'left',
                          fontWeight: 'bold',
                          fontSize: '14px'
                        }}>Hours</th>
                        <th style={{
                          padding: '12px',
                          textAlign: 'left',
                          fontWeight: 'bold',
                          fontSize: '14px'
                        }}>Late</th>
                        <th style={{
                          padding: '12px',
                          textAlign: 'left',
                          fontWeight: 'bold',
                          fontSize: '14px'
                        }}>Status</th>
                        <th style={{
                          padding: '12px',
                          textAlign: 'center',
                          fontWeight: 'bold',
                          fontSize: '14px'
                        }}>Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {todayAttendance.map((record, index) => {
                        const employee = employees.find(emp => emp.id === record.employeeId);
                        const shift = getEmployeeShiftForToday(record.employeeId, scheduleData);
                        const clockInTime = record.clockIn ? new Date(record.clockIn).toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'}) : '--';
                        const clockOutTime = record.clockOut ? new Date(record.clockOut).toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'}) : '--';
                        const isCurrentlyClockedIn = getEmployeeClockStatus(record.employeeId);
                        const empDetails = employeeDetails[record.employeeId];

                        return (
                          <tr key={index} style={{
                            borderBottom: '1px solid ' + (darkMode ? '#444' : '#e9ecef'),
                            backgroundColor: index % 2 === 0 ?
                              (darkMode ? '#2d2d2d' : '#f8f9fa') :
                              (darkMode ? '#3d3d3d' : '#ffffff'),
                            transition: 'background-color 0.3s ease'
                          }}>
                            <td style={{
                              padding: '12px',
                              fontWeight: 'bold'
                            }}>
                              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                                <div style={{
                                  width: '8px',
                                  height: '8px',
                                  backgroundColor: isCurrentlyClockedIn ? '#4CAF50' : '#F44336',
                                  borderRadius: '50%',
                                  animation: isCurrentlyClockedIn ? 'pulse 2s infinite' : 'none'
                                }}></div>
                                <div>
                                  <div>{employee?.title || record.employeeId}</div>
                                  {empDetails && (
                                    <div style={{
                                      fontSize: '11px',
                                      opacity: 0.7,
                                      color: darkMode ? '#cccccc' : '#666'
                                    }}>
                                      {empDetails.department}
                                    </div>
                                  )}
                                </div>
                              </div>
                            </td>
                            <td style={{ padding: '12px' }}>
                              <div style={{
                                padding: '4px 8px',
                                backgroundColor: shift ? SHIFT_CONFIG[shift.shiftName]?.color + '20' : 'transparent',
                                borderRadius: '4px',
                                display: 'inline-block',
                                borderLeft: '4px solid ' + (shift ? SHIFT_CONFIG[shift.shiftName]?.color : '#607D8B')
                              }}>
                                {shift?.shiftName || '--'}
                              </div>
                            </td>
                            <td style={{ padding: '12px', fontWeight: 'bold' }}>
                              {clockInTime}
                            </td>
                            <td style={{ padding: '12px', fontWeight: 'bold' }}>
                              {clockOutTime}
                            </td>
                            <td style={{ padding: '12px' }}>
                              <div style={{
                                display: 'flex',
                                alignItems: 'center',
                                gap: '5px'
                              }}>
                                <span style={{ fontWeight: 'bold' }}>
                                  {record.hoursWorked?.toFixed(2) || '--'}h
                                </span>
                                {record.overtimeHours > 0 && (
                                  <span style={{
                                    fontSize: '11px',
                                    padding: '2px 6px',
                                    backgroundColor: '#FF9800',
                                    color: 'white',
                                    borderRadius: '4px',
                                    fontWeight: 'bold'
                                  }}>
                                    +{record.overtimeHours.toFixed(1)}h OT
                                  </span>
                                )}
                              </div>
                            </td>
                            <td style={{ padding: '12px' }}>
                              {record.lateMinutes > 0 ? (
                                <span style={{
                                  color: '#FF9800',
                                  fontWeight: 'bold',
                                  display: 'flex',
                                  alignItems: 'center',
                                  gap: '3px'
                                }}>
                                  ⚠️ {formatLateTime(record.lateMinutes)}
                                </span>
                              ) : (
                                <span style={{ color: '#4CAF50' }}>✓ On Time</span>
                              )}
                            </td>
                            <td style={{ padding: '12px' }}>
                              {getAttendanceStatusBadge(record.attendanceStatus)}
                            </td>
                            <td style={{ padding: '12px', textAlign: 'center' }}>
                              {isCurrentlyClockedIn && (
                                <button
                                  onClick={() => handleClockOut(record.employeeId)}
                                  style={{
                                    padding: '6px 12px',
                                    backgroundColor: '#F44336',
                                    color: 'white',
                                    border: 'none',
                                    borderRadius: '4px',
                                    fontWeight: 'bold',
                                    fontSize: '12px',
                                    cursor: 'pointer',
                                    transition: 'all 0.3s ease'
                                  }}
                                  onMouseEnter={(e) => {
                                    e.target.style.transform = 'scale(1.05)';
                                    e.target.style.boxShadow = '0 4px 8px rgba(0,0,0,0.2)';
                                  }}
                                  onMouseLeave={(e) => {
                                    e.target.style.transform = 'scale(1)';
                                    e.target.style.boxShadow = 'none';
                                  }}
                                >
                                  Clock Out
                                </button>
                              )}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              )}
            </div>

            {/* Quick Actions Footer */}
            <div style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              marginTop: '20px',
              padding: '15px',
              backgroundColor: darkMode ? '#2d2d2d' : '#f8f9fa',
              borderRadius: '8px',
              border: '2px solid ' + (darkMode ? '#444' : '#e9ecef'),
              width: '100%'
            }}>
              <div style={{
                display: 'flex',
                alignItems: 'center',
                gap: '15px',
                flexWrap: 'wrap'
              }}>
                <div style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '5px',
                  fontSize: '13px'
                }}>
                  <div style={{
                    width: '10px',
                    height: '10px',
                    backgroundColor: '#4CAF50',
                    borderRadius: '2px'
                  }}></div>
                  <span>Available</span>
                </div>
                <div style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '5px',
                  fontSize: '13px'
                }}>
                  <div style={{
                    width: '10px',
                    height: '10px',
                    backgroundColor: '#F44336',
                    borderRadius: '2px'
                  }}></div>
                  <span>Unavailable</span>
                </div>
                <div style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '5px',
                  fontSize: '13px'
                }}>
                  <div style={{
                    width: '10px',
                    height: '10px',
                    backgroundColor: '#FF9800',
                    borderRadius: '2px'
                  }}></div>
                  <span>Late</span>
                </div>
                <div style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '5px',
                  fontSize: '13px'
                }}>
                  <div style={{
                    width: '10px',
                    height: '10px',
                    backgroundColor: '#4CAF50',
                    borderRadius: '50%',
                    animation: 'pulse 2s infinite'
                  }}></div>
                  <span>Clocked In</span>
                </div>
              </div>

              <div style={{ display: 'flex', gap: '10px' }}>
                {clockedInEmployees.length > 0 && (
                  <>
                    <button
                      onClick={() => {
                        if (window.confirm('Are you sure you want to clock out all ' + clockedInEmployees.length + ' employees?')) {
                          clockedInEmployees.forEach(employeeId => handleClockOut(employeeId));
                        }
                      }}
                      style={{
                        ...getButtonStyle('warning'),
                        padding: '8px 16px',
                        fontSize: '14px'
                      }}
                    >
                      ⚡ Clock Out All
                    </button>
                    <button
                      onClick={() => {
                        clockedInEmployees.forEach(employeeId => handleBreakStart(employeeId));
                      }}
                      style={{
                        ...getButtonStyle('info'),
                        padding: '8px 16px',
                        fontSize: '14px'
                      }}
                    >
                      ⏸️ Start Break All
                    </button>
                  </>
                )}
                <button
                  onClick={() => {
                    setShowTimeClock(false);
                    setTimeClockSelectedEmployee('');
                  }}
                  style={{
                    ...getButtonStyle('secondary'),
                    padding: '8px 16px',
                    fontSize: '14px'
                  }}
                >
                  Close Time Clock
                </button>
              </div>
            </div>
          </div>
        )}
            {scheduleData?.slots && (
              <div style={{
                padding: '20px',
                background: 'linear-gradient(135deg, #4caf50, #45a049)',
                color: 'white',
                borderRadius: '12px',
                textAlign: 'center',
                margin: '20px 0',
                boxShadow: '0 4px 15px rgba(76,175,80,0.3)'
              }}>
                <h3 style={{ margin: '0 0 10px 0' }}>💰 Smart Cost Optimization Active</h3>
                <div style={{ fontSize: '28px', fontWeight: 'bold', margin: '10px 0' }}>
                  Estimated Daily Payroll: $
                  {scheduleData.slots.reduce((total, slot) => {
                    return total + (slot.employees || []).reduce((sum, emp) => {
                      const wage = employeeDetails[emp.id]?.hourlyWage || 0;
                      return sum + wage * 8;
                    }, 0);
                  }, 0).toFixed(0)}
                </div>
                <p style={{ margin: 0, opacity: 0.9 }}>
                  Lower-paid qualified employees are automatically preferred
                </p>
              </div>
            )}
        {/* FullCalendar - Main Content */}
        <div style={{
          height: 700,
          border: '2px solid ' + (darkMode ? '#444' : '#dee2e6'),
          borderRadius: 8,
          overflow: 'hidden',
          backgroundColor: darkMode ? '#2d2d2d' : 'white',
          marginBottom: 20,
          width: '100%'
        }}>
          <FullCalendar
            ref={calendarRef}
            plugins={[
              resourceTimelinePlugin,
              dayGridPlugin,
              timeGridPlugin,
              interactionPlugin
            ]}
            headerToolbar={{
              left: 'prev,next today',
              center: 'title',
              right: 'resourceTimelineDay,resourceTimelineWeek,resourceTimelineMonth'
            }}
            initialView={calendarView}
            views={{
              resourceTimelineDay: {
                type: 'resourceTimeline',
                duration: { days: 1 },
                slotDuration: '01:00:00'
              },
              resourceTimelineWeek: {
                type: 'resourceTimeline',
                duration: { weeks: 1 },
                slotDuration: '01:00:00'
              },
              resourceTimelineMonth: {
                type: 'resourceTimeline',
                duration: { months: 1 },
                slotDuration: '24:00:00'
              }
            }}
            resources={calendarResources}
            events={calendarEvents}
            eventContent={renderEventContent}
            eventClick={handleEventClick}
            eventDrop={handleEventDrop}
            editable={true}
            droppable={true}
            resourceAreaWidth="200px"
            resourceAreaColumns={[
              {
                field: 'title',
                headerContent: 'Employee'
              },
              {
                field: 'department',
                headerContent: 'Department'
              }
            ]}
            slotMinTime="06:00:00"
            slotMaxTime="24:00:00"
            height="100%"
            expandRows={true}
            nowIndicator={true}
            dayMaxEvents={true}
            eventDisplay="block"
            eventTimeFormat={{
              hour: '2-digit',
              minute: '2-digit',
              hour12: false
            }}
            resourceLabelContent={(arg) => {
              const resource = arg.resource;
              const empDetails = employeeDetails[resource.id];

              // Fallback values
              const name = resource.title || 'Unknown Employee';
              const department = empDetails?.department || 'Unknown';
              const position = empDetails?.position || '';
              const hourlyWage = empDetails?.hourlyWage || 0;
              const rating = empDetails?.performanceRating || 3; // Default 3



              // Department color
              const deptColor = DEPARTMENT_COLORS[department]?.color || '#607D8B';

              // Performance indicators
              const filledStars = '⭐'.repeat(rating);
              const emptyStars = '☆'.repeat(5 - rating);
              const isTopPerformer = rating >= 4;
              const isLowPerformer = rating <= 2;

              // Status indicators
              const hasLeaves = calendarEvents.some(event =>
                event.extendedProps?.type === 'leave' && event.resourceId === resource.id
              );
              const hasViolations = complianceViolations.some(v =>
                v.employeeId === resource.id && !v.resolved
              );
              const hasCoverage = coverageAssignments.some(ca =>
                ca.assignedEmployeeId === resource.id && ca.status !== 'COMPLETED'
              );

              return {
                html: `
                  <div style="
                    display: flex;
                    flex-direction: column;
                    justify-content: center;
                    height: 100%;
                    padding: 6px 10px;
                    font-size: 13px;
                    line-height: 1.4;
                    box-sizing: border-box;
                  ">
                    <!-- Name + Icons -->
                    <div style="
                      font-weight: bold;
                      font-size: 14.5px;
                      color: ${darkMode ? '#ffffff' : '#333'};
                      margin-bottom: 4px;
                      display: flex;
                      align-items: center;
                      gap: 6px;
                      flex-wrap: wrap;
                    ">
                      ${name}
                      ${isTopPerformer ? '<span title="Top Performer - Prioritized by Scheduler" style="font-size:18px;">🌟</span>' : ''}
                      ${isLowPerformer ? '<span title="Needs Improvement" style="font-size:16px; color:#ff9800;">⚡</span>' : ''}
                      ${hasLeaves ? '<span title="On Leave" style="font-size:16px;">🏖️</span>' : ''}
                      ${hasViolations ? '<span title="Compliance Issue" style="font-size:16px; color:#f44336;">⚠️</span>' : ''}
                      ${hasCoverage ? '<span title="OT Coverage Duty" style="font-size:16px; color:#FF9800;">🔄</span>' : ''}
                    </div>

                    <!-- Department & Position -->
                    <div style="
                      font-size: 11.5px;
                      color: ${deptColor};
                      font-weight: bold;
                      margin-bottom: 6px;
                    ">
                      ${department} • ${position}
                    </div>

                    <!-- Star Rating -->
                    <div style="
                      margin-bottom: 8px;
                      display: flex;
                      align-items: center;
                      gap: 8px;
                    ">
                      <span style="font-size: 16px; letter-spacing: 2px;">
                        ${filledStars}${emptyStars}
                      </span>
                      <span style="
                        font-size: 11px;
                        color: ${darkMode ? '#aaa' : '#777'};
                      ">
                        Performance Rating (${rating}/5)
                      </span>
                    </div>

                    <!-- Wage + Smart Badge -->
                    <div style="
                      display: flex;
                      align-items: center;
                      justify-content: space-between;
                      flex-wrap: wrap;
                      gap: 8px;
                    ">
                      <span style="
                        background: ${darkMode ? '#333' : '#f0f0f0'};
                        color: ${darkMode ? '#fff' : '#333'};
                        padding: 4px 10px;
                        border-radius: 20px;
                        font-weight: bold;
                        font-size: 12px;
                        border: 1px solid ${darkMode ? '#555' : '#ddd'};
                      ">
                        $${hourlyWage.toFixed(0)}/hr
                      </span>

                      ${isTopPerformer ? `
                        <span style="
                          background: linear-gradient(135deg, #4CAF50, #388E3C);
                          color: white;
                          padding: 4px 10px;
                          border-radius: 20px;
                          font-size: 11px;
                          font-weight: bold;
                          box-shadow: 0 2px 6px rgba(76,175,80,0.4);
                        ">
                          Preferred
                        </span>
                      ` : isLowPerformer ? `
                        <span style="
                          background: ${darkMode ? '#5c4a1e' : '#fff3cd'};
                          color: ${darkMode ? '#fff' : '#856404'};
                          padding: 4px 10px;
                          border-radius: 20px;
                          font-size: 11px;
                          font-weight: bold;
                          border: 1px solid ${darkMode ? '#7a6128' : '#ffeaa7'};
                        ">
                          Needs Improvement
                        </span>
                      ` : ''}
                    </div>
                  </div>
                `
              };
            }}
          />
            {showAssignmentPanel && <AssignmentPanel />}
        </div>
      </div>
    </div>
  );
}

export default App;
