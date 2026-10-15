document.addEventListener('DOMContentLoaded', function() {
    // 1. Core API Request Wrapper with Auth Headers
    const token = localStorage.getItem('token');
    
    async function apiRequest(url, options = {}) {
        const headers = {
            'Content-Type': 'application/json',
            ...(token ? { 'Authorization': `Bearer ${token}` } : {}),
            ...options.headers
        };
        
        const response = await fetch(url, { ...options, headers });
        
        if (response.status === 401 || response.status === 403) {
            // Clear expired session and redirect
            localStorage.clear();
            window.location.href = '/login?error';
            return null;
        }
        
        if (!response.ok) {
            const errorObj = await response.json().catch(() => ({}));
            throw new Error(errorObj.message || `API request failed with status ${response.status}`);
        }
        
        if (response.status === 204) return null; // No Content
        return await response.json();
    }

    // 2. Alert Handler
    const ajaxAlert = document.getElementById('ajaxAlert');
    const ajaxAlertMsg = document.getElementById('ajaxAlertMessage');
    const ajaxAlertIcon = document.getElementById('ajaxAlertIcon');

    function showNotification(message, type = 'success') {
        if (!ajaxAlert) return;
        
        ajaxAlert.className = `alert border-0 small rounded-3 mb-4 animate-fade-in ${
            type === 'success' ? 'alert-success bg-success bg-opacity-10 text-success' : 'alert-danger bg-danger bg-opacity-10 text-danger'
        }`;
        
        if (type === 'success') {
            ajaxAlertIcon.className = 'fa-solid fa-circle-check me-2';
        } else {
            ajaxAlertIcon.className = 'fa-solid fa-circle-exclamation me-2';
        }
        
        ajaxAlertMsg.textContent = message;
        ajaxAlert.classList.remove('d-none');
        
        // Scroll to alert
        ajaxAlert.scrollIntoView({ behavior: 'smooth', block: 'end' });
    }

    // Helper to extract numeric values from raw strings
    function parseAmount(amtStr) {
        return parseFloat(amtStr.replace(/[^0-9.-]+/g, ""));
    }

    // 3. Dynamic Balance and Stat Refresh
    async function refreshDashboardData() {
        try {
            // Get user accounts
            const accounts = await apiRequest('/api/accounts/my');
            if (!accounts || accounts.length === 0) return;
            
            let combinedBalance = 0;

            // Update Account Cards Balance in DOM
            accounts.forEach(acc => {
                const balanceElements = document.querySelectorAll(`.account-balance-val[th\\:data-acc="${acc.accountNumber}"], .account-balance-val[data-acc="${acc.accountNumber}"]`);
                const formattedBalance = parseFloat(acc.balance).toLocaleString('en-IN', {
                    minimumFractionDigits: 2,
                    maximumFractionDigits: 2
                });
                
                balanceElements.forEach(el => {
                    el.textContent = formattedBalance;
                });
                
                combinedBalance += parseFloat(acc.balance);
            });

            // Update Combined Net Position
            const netStatEl = document.getElementById('statNet');
            if (netStatEl) {
                netStatEl.textContent = '₹' + combinedBalance.toLocaleString('en-IN', {
                    minimumFractionDigits: 2,
                    maximumFractionDigits: 2
                });
            }

            // Fetch transaction history of the primary account (or first account)
            const primaryAccNum = accounts[0].accountNumber;
            const transactions = await apiRequest(`/api/transactions/history/${primaryAccNum}`);
            
            if (transactions) {
                // Update transaction statistics
                let totalDeposits = 0;
                let totalWithdrawals = 0;

                transactions.forEach(tx => {
                    const amt = parseFloat(tx.amount);
                    if (tx.type === 'DEPOSIT') {
                        totalDeposits += amt;
                    } else if (tx.type === 'WITHDRAWAL') {
                        totalWithdrawals += amt;
                    } else if (tx.type === 'TRANSFER') {
                        // If current account is source, it is outflow; if destination, it is inflow
                        if (tx.sourceAccountNumber === primaryAccNum) {
                            totalWithdrawals += amt;
                        } else if (tx.destinationAccountNumber === primaryAccNum) {
                            totalDeposits += amt;
                        }
                    }
                });

                const statDepositsEl = document.getElementById('statDeposits');
                if (statDepositsEl) {
                    statDepositsEl.textContent = '₹' + totalDeposits.toLocaleString('en-IN', {
                        minimumFractionDigits: 2,
                        maximumFractionDigits: 2
                    });
                }

                const statWithdrawalsEl = document.getElementById('statWithdrawals');
                if (statWithdrawalsEl) {
                    statWithdrawalsEl.textContent = '₹' + totalWithdrawals.toLocaleString('en-IN', {
                        minimumFractionDigits: 2,
                        maximumFractionDigits: 2
                    });
                }

                // Update recent transactions table
                const txTableBody = document.getElementById('dashboardTxBody');
                if (txTableBody) {
                    txTableBody.innerHTML = ''; // Clear previous entries
                    
                    const recentTxs = transactions.slice(0, 5);
                    if (recentTxs.length === 0) {
                        txTableBody.innerHTML = `<tr><td colspan="5" class="text-center text-secondary py-4">No recent ledger data logged.</td></tr>`;
                    } else {
                        recentTxs.forEach(tx => {
                            const isDeposit = tx.type === 'DEPOSIT' || (tx.type === 'TRANSFER' && tx.destinationAccountNumber === primaryAccNum);
                            const amtSign = isDeposit ? '+' : '-';
                            const amtClass = isDeposit ? 'text-end text-success fw-bold' : 'text-end text-danger fw-bold';
                            
                            const txDate = new Date(tx.timestamp);
                            const formattedDate = txDate.toLocaleDateString('en-IN', { day: '2-digit', month: 'short' }) + ' ' + 
                                                txDate.toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit', hour12: false });

                            const row = document.createElement('tr');
                            row.className = 'db-tx-row animate-fade-in';
                            row.innerHTML = `
                                <td><span class="font-monospace text-secondary small text-white">${tx.transactionReference.substring(0, 8)}</span></td>
                                <td class="small">${formattedDate}</td>
                                <td>
                                    <span class="badge ${
                                        tx.type === 'DEPOSIT' ? 'bg-success' : tx.type === 'WITHDRAWAL' ? 'bg-danger' : 'bg-warning'
                                    } bg-opacity-10 ${
                                        tx.type === 'DEPOSIT' ? 'text-success' : tx.type === 'WITHDRAWAL' ? 'text-danger' : 'text-warning'
                                    } border ${
                                        tx.type === 'DEPOSIT' ? 'border-success' : tx.type === 'WITHDRAWAL' ? 'border-danger' : 'border-warning'
                                    } border-opacity-20 small px-2">${tx.type.substring(0, 3)}</span>
                                </td>
                                <td class="small">
                                    <span>${tx.description}</span>
                                    <div class="text-secondary mt-1">
                                        ${tx.sourceAccountNumber ? `<span>From: <strong>${tx.sourceAccountNumber}</strong></span>` : ''}
                                        ${tx.destinationAccountNumber ? `<span>To: <strong>${tx.destinationAccountNumber}</strong></span>` : ''}
                                    </div>
                                </td>
                                <td class="${amtClass}">
                                    ${amtSign}₹${parseFloat(tx.amount).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                                </td>
                            `;
                            txTableBody.appendChild(row);
                        });
                    }
                }
            }

        } catch (err) {
            console.error('Error refreshing dashboard data:', err);
        }
    }

    // 4. Modal Submission Forms Handling
    function setupFormHandler(formId, modalId, apiEndpoint, getRequestBody, successMessage) {
        const form = document.getElementById(formId);
        if (!form) return;

        form.addEventListener('submit', async function(e) {
            e.preventDefault();
            
            const submitBtn = form.querySelector('button[type="submit"]');
            const spinner = submitBtn.querySelector('.spinner-border');
            const btnText = submitBtn.querySelector('span:not(.spinner-border)');
            
            // Set loading state
            submitBtn.disabled = true;
            if (spinner) spinner.classList.remove('d-none');
            const originalText = btnText ? btnText.textContent : 'Submit';
            if (btnText) btnText.textContent = 'Processing...';

            try {
                const requestBody = getRequestBody(form);
                const result = await apiRequest(apiEndpoint, {
                    method: 'POST',
                    body: JSON.stringify(requestBody)
                });

                if (result) {
                    showNotification(successMessage || 'Operation completed successfully!');
                    
                    // Reset form and close modal
                    form.reset();
                    const modalEl = document.getElementById(modalId);
                    if (modalEl) {
                        const modalInstance = bootstrap.Modal.getInstance(modalEl);
                        if (modalInstance) modalInstance.hide();
                    }
                    
                    // Dynamic balance refresh
                    await refreshDashboardData();
                }
            } catch (err) {
                showNotification(err.message || 'Operation failed. Please review inputs.', 'danger');
            } finally {
                // Restore button state
                submitBtn.disabled = false;
                if (spinner) spinner.classList.add('d-none');
                if (btnText) btnText.textContent = originalText;
            }
        });
    }

    // Configure specific forms
    setupFormHandler(
        'depositForm',
        'depositModal',
        '/api/transactions/deposit',
        (form) => ({
            accountNumber: form.accountNumber.value,
            amount: parseFloat(form.amount.value)
        }),
        'Cash deposited in ledger account successfully!'
    );

    setupFormHandler(
        'withdrawForm',
        'withdrawModal',
        '/api/transactions/withdraw',
        (form) => ({
            accountNumber: form.accountNumber.value,
            amount: parseFloat(form.amount.value)
        }),
        'Cash withdrawn from ledger account successfully!'
    );

    setupFormHandler(
        'transferForm',
        'transferModal',
        '/api/transactions/transfer',
        (form) => ({
            sourceAccountNumber: form.sourceAccountNumber.value,
            destinationAccountNumber: form.destinationAccountNumber.value,
            amount: parseFloat(form.amount.value),
            description: form.description.value
        }),
        'Fund transfer completed successfully!'
    );

    // Sync Transfer Beneficiary Select to Manual Input field
    const tfBenSelect = document.getElementById('transferBeneficiarySelect');
    const tfDestInput = document.getElementById('transferDestAccountInput');
    if (tfBenSelect && tfDestInput) {
        tfBenSelect.addEventListener('change', function() {
            tfDestInput.value = this.value;
        });
    }

    // 5. Add Beneficiary Asynchronously
    const addBenForm = document.getElementById('addBeneficiaryForm');
    if (addBenForm) {
        addBenForm.addEventListener('submit', async function(e) {
            e.preventDefault();
            
            const submitBtn = addBenForm.querySelector('button[type="submit"]');
            const spinner = submitBtn.querySelector('.spinner-border');
            const btnText = submitBtn.querySelector('span:not(.spinner-border)');
            
            submitBtn.disabled = true;
            if (spinner) spinner.classList.remove('d-none');
            const originalText = btnText ? btnText.textContent : 'Submit';
            if (btnText) btnText.textContent = 'Saving Recipient...';

            try {
                const requestBody = {
                    name: addBenForm.name.value,
                    accountNumber: addBenForm.accountNumber.value,
                    ifscCode: addBenForm.ifscCode.value,
                    bankName: addBenForm.bankName.value
                };

                const newBen = await apiRequest('/api/beneficiaries', {
                    method: 'POST',
                    body: JSON.stringify(requestBody)
                });

                if (newBen) {
                    showNotification('Beneficiary recipient registered successfully!');
                    
                    // Reset and close
                    addBenForm.reset();
                    const modalEl = document.getElementById('addBeneficiaryModal');
                    if (modalEl) {
                        const modalInstance = bootstrap.Modal.getInstance(modalEl);
                        if (modalInstance) modalInstance.hide();
                    }

                    // Dynamically prepend new beneficiary to container list
                    const container = document.getElementById('beneficiaryContainer');
                    const noMsg = document.getElementById('noBeneficiaryMsg');
                    if (noMsg) noMsg.style.display = 'none';

                    const newRow = document.createElement('div');
                    newRow.id = `ben-row-${newBen.id}`;
                    newRow.className = 'list-group-item bg-transparent border-opacity-10 px-0 d-flex justify-content-between align-items-center animate-fade-in';
                    newRow.style.borderColor = 'var(--border-color)';
                    newRow.innerHTML = `
                        <div>
                            <h6 class="fw-bold mb-0 text-white">${newBen.name}</h6>
                            <span class="x-small text-secondary"><strong class="text-white-50">${newBen.bankName || 'APEX'}</strong> - ${newBen.accountNumber}</span>
                        </div>
                        <button class="btn btn-sm btn-outline-danger border-0 delete-ben-btn" data-id="${newBen.id}"><i class="fa-solid fa-trash-can"></i></button>
                    `;
                    container.insertBefore(newRow, container.firstChild);
                    
                    // Re-bind delete listener
                    bindDeleteListener(newRow.querySelector('.delete-ben-btn'));

                    // Also dynamically add option to transfer beneficiary dropdown list
                    if (tfBenSelect) {
                        const opt = document.createElement('option');
                        opt.value = newBen.accountNumber;
                        opt.textContent = `${newBen.name} (${newBen.accountNumber})`;
                        tfBenSelect.appendChild(opt);
                    }
                }
            } catch (err) {
                showNotification(err.message || 'Error occurred while saving beneficiary.', 'danger');
            } finally {
                submitBtn.disabled = false;
                if (spinner) spinner.classList.add('d-none');
                if (btnText) btnText.textContent = originalText;
            }
        });
    }

    // 6. Delete Beneficiary Asynchronously
    function bindDeleteListener(btn) {
        if (!btn) return;
        btn.addEventListener('click', async function() {
            const benId = this.getAttribute('data-id');
            if (!confirm('Are you sure you want to delete this beneficiary recipient node?')) return;
            
            this.disabled = true;

            try {
                await apiRequest(`/api/beneficiaries/${benId}`, {
                    method: 'DELETE'
                });

                showNotification('Beneficiary node deleted successfully.');
                
                // Remove row from UI
                const row = document.getElementById(`ben-row-${benId}`);
                if (row) row.remove();

                const container = document.getElementById('beneficiaryContainer');
                const rows = container.querySelectorAll('.list-group-item');
                if (rows.length === 0) {
                    const noMsg = document.getElementById('noBeneficiaryMsg');
                    if (noMsg) noMsg.style.display = '';
                    else {
                        container.innerHTML = `<div class="text-center text-secondary py-3 small" id="noBeneficiaryMsg">No node recipients configured.</div>`;
                    }
                }
            } catch (err) {
                showNotification(err.message || 'Error deleting beneficiary.', 'danger');
                this.disabled = false;
            }
        });
    }

    // Bind all initial delete buttons
    document.querySelectorAll('.delete-ben-btn').forEach(btn => bindDeleteListener(btn));

    // 7. Mobile Sidebar Drawer Logic
    const sidebarToggleBtn = document.getElementById('sidebarToggleBtn');
    const closeSidebarBtn = document.getElementById('closeSidebarBtn');
    const appSidebar = document.getElementById('appSidebar');
    const sidebarBackdrop = document.getElementById('sidebarBackdrop');

    function openMobileSidebar() {
        if (appSidebar) appSidebar.classList.add('show');
        if (sidebarBackdrop) sidebarBackdrop.classList.remove('d-none');
    }

    function closeMobileSidebar() {
        if (appSidebar) appSidebar.classList.remove('show');
        if (sidebarBackdrop) sidebarBackdrop.classList.add('d-none');
    }

    if (sidebarToggleBtn) {
        sidebarToggleBtn.addEventListener('click', openMobileSidebar);
    }
    if (closeSidebarBtn) {
        closeSidebarBtn.addEventListener('click', closeMobileSidebar);
    }
    if (sidebarBackdrop) {
        sidebarBackdrop.addEventListener('click', closeMobileSidebar);
    }

    // 8. Prevent mouse wheel scroll from changing number inputs and sanitize floating point precision (e.g. 500 -> 499.99)
    document.addEventListener('wheel', function(e) {
        if (document.activeElement && document.activeElement.type === 'number') {
            document.activeElement.blur();
        }
    }, { passive: true });

    document.addEventListener('change', function(e) {
        if (e.target && e.target.type === 'number' && e.target.value !== '') {
            const raw = parseFloat(e.target.value);
            if (!isNaN(raw)) {
                const clean = Math.round((raw + Number.EPSILON) * 100) / 100;
                if (clean.toString() !== e.target.value) {
                    e.target.value = clean;
                }
            }
        }
    });

    document.addEventListener('blur', function(e) {
        if (e.target && e.target.type === 'number' && e.target.value !== '') {
            const raw = parseFloat(e.target.value);
            if (!isNaN(raw)) {
                const clean = Math.round((raw + Number.EPSILON) * 100) / 100;
                if (clean.toString() !== e.target.value) {
                    e.target.value = clean;
                }
            }
        }
    }, true);
});
